(ns citron.http
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [citron.db :as db]
   [citron.utils :as utils]
   
   [clojure.core.async :as a :refer [chan]]
   [ajax.core :refer [PUT POST GET DELETE json-response-format json-request-format]]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [reagent.session :as session]
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
   (swap! db/app-state assoc :page-loading true :error nil)
   (GET "/file"
        {:params {:path path :offset offset}
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

(defn put-msg-board [msg]
  (PUT "/msg" 
       {:params {:msg msg}
        :format :json
        :response-format :json
        :keywords? true
        :timeout 60000
        :handler (fn [{:keys [code msg]}]
                   (when-not (zero? code)
                     (swap! db/app-state assoc :error msg)))
        :error-handler (http-error-handler "/msg")}))

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
