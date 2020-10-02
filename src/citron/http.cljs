(ns citron.http
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [citron.db :as db]
   [citron.utils :as utils]
   
   [clojure.core.async :as a :refer [chan]]
   [ajax.core :refer [PUT POST GET DELETE json-response-format json-request-format]]
   [clojure.string :as s]
   [taoensso.timbre :as t]))

(defn http-error-handler [uri]
  (fn [err]
    (if (= 403 (:status err))
      (swap! db/app-state dissoc :user)
      (do
        (t/error uri err)
        (swap! db/app-state assoc :error (str "Error: " err))))))


(defn set-done! [ch]
  (fn []
    (a/put! ch ::done)))

(defn- merge-filelist [{:keys [files]} f2]
  (assoc f2 :files (concat files (:files f2))))

(defn get-file
  "Get file info for the provided path"
  ([]
   (get-file "."))
  ([path]
   (get-file path 0))
  ([path offset]
   (swap! db/app-state dissoc :filter? :filter-term)
   (get-file path offset ""))
  ([path offset term]
   (swap! db/app-state assoc :page-loading true :error nil)
   (GET "/file"
        {:params {:path path :offset offset :term term}
         :handler (fn [{:keys [code data msg]}]
                    (if (zero? code)
                      (if (zero? offset)
                        (reset! db/file-store data)
                        (swap! db/file-store merge-filelist data))
                      (swap! db/app-state assoc :error msg)))
         :response-format :json
         :keywords? true
         :finally #(do
                     (swap! db/app-state dissoc :page-loading :rename-file?)
                     (db/reset-playback-speed))
         :error-handler (http-error-handler "/file")})))

(defn upload-file [{:keys [file parent]} on-success]
  (PUT "/file" 
       {:body (doto (js/FormData.)
                (.append "file" file (.-name file))
                (.append "parent" parent))
        :response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code msg] :as resp}]
                   (if (zero? code)
                     (on-success)
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/file")}))

(defn delete-file [f on-success]
  (DELETE "/file" 
          {:params f
           :format :json
           :response-format :json
           :keywords? true
           :timeout 60000
           :handler (fn [{:keys [code msg]}]
                      (if (zero? code)
                        (on-success)
                        (swap! db/app-state assoc :error msg)))
           :error-handler (http-error-handler "/file")}))

(defn rename-file [path filename]
  (POST "/rename" 
       {:params {:path path :filename filename}
        :format :json
        :response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code msg]}]
                   (if (zero? code)
                     (get-file (s/replace path (utils/to-filename path) filename))
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/rename")}))

(defn get-msg-board []
  (GET "/msg" 
       {:response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code data msg]}]
                   (if (zero? code)
                     (reset! db/msg-store data)
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/msg")}))

(defn put-msg-board [msg]
  (PUT "/msg" 
       {:params {:msg msg}
        :format :json
        :response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code msg]}]
                   (if (zero? code)
                     (do
                       (reset! db/temp-msg-store "")
                       (get-msg-board))
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/msg")}))


(defn delete-msg [id]
  (DELETE "/msg" 
       {:params {:id id}
        :format :json
        :response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code msg]}]
                   (if (zero? code)
                     (get-msg-board)
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/msg")}))

(defn login [cred]
  (swap! db/app-state dissoc :error)
  (POST "/pub/login"
        {:params cred
         :format :json
         :handler (fn [{:keys [code data msg]}]
                    (if (zero? code)
                      (do
                        (swap! db/app-state assoc :user data)
                        (get-msg-board)
                        (get-file))
                      (swap! db/app-state assoc :error msg)))
         :response-format :json
         :keywords? true
         :error-handler (http-error-handler "/pub/login")}))
