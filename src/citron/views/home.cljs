(ns citron.views.home
  (:require
   [citron.db :as db]
   [citron.http :as http]
   [citron.utils :refer [set-hash!]]

   [ajax.core :refer [PUT GET DELETE json-response-format json-request-format]]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [reagent.session :as session]
   [clojure.string :as s]
   [taoensso.timbre :as t]

   [cljs.reader :as reader]

   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import
   [goog History date]))

(defn parent [path]
  (s/join "/" (butlast (s/split path #"/"))))

(defn top? [path]
  (#{"." "./" ""} path))

(defn set-login-value [key]
  (fn [e]
    (swap! db/login-store assoc key (-> e .-target .-value))))

(defn login []
  (when-not (some s/blank? (vals @db/login-store))
    (http/login @db/login-store)))

(defn goto-file [{:keys [path]}]
  (http/get-file path))

(defn goto-parent [{:keys [path]}]
  (goto-file {:path (parent path)}))

(defn edit-msg-board []
  (let [{:keys [edit-msg-board]} (swap! db/app-state update :edit-msg-board not)]
    (when-not edit-msg-board
      (http/put-msg-board @db/msg-store))))

(defn- msg-board []
  (fn []
    [:div
     [:div "Message board"
      [:input {:type :button
               :value (if (:edit-msg-board @db/app-state)
                        "Save"
                        "Edit")
               :on-click edit-msg-board}]]
     (if (:edit-msg-board @db/app-state)
       [:textarea {:value @db/msg-store :on-change (fn [e] (reset! db/msg-store (-> e .-target .-value)))}]
       [:pre @db/msg-store])]))

(defn make-static-path [path]
  (str "/static/file?path=" path))

(defn- user-page []
  (fn []
    [:div 
     [:div (str "hi, " (get-in @db/app-state [:user :username] ""))]
     [msg-board]
     (let [{:keys [path files isdir content mime] :as f} @db/file-store]
       [:div path
        [:div
         [:input {:type :button
                  :value "UP"
                  :disabled (top? path)
                  :on-click #(goto-parent f)}]
         [:input {:type :button
                  :value "DEL"
                  :disabled (and isdir (pos? (count files)))
                  :on-click #(http/delete-file f (fn [] (goto-parent f)))}]
         (when-not isdir
           [:a {:type :button :href (make-static-path path) :target "_blank"} "Open"])
         (when isdir
           [:input {:type :file
                    :on-change (fn [e]
                                 (let [file (first (array-seq (.. e -target -files)))
                                       filename (when file (.-name file))]
                                   (t/info "uploading file" filename)
                                   (when filename
                                     (http/upload-file
                                      (assoc @db/tmp-file-store :file file :parent path)
                                      (fn []
                                        (http/get-file path))))))}])]
        (when content
          [:div
           [:pre content]])
        (when (s/includes? (or mime "") "image")
          [:div
           [:img {:src (make-static-path path)}]])
        (doall
         (for [{:keys [isdir path mime] :as f} (sort-by :path files)]
           [:div {:key path :on-click #(goto-file f)}
            [:span (str path
                        (when (and isdir (not (s/ends-with? path "/")))
                          "/"))]
            "----------"
            [:span (if isdir "Directory" (or mime ""))]]))])]))

(defn index-page []
  (fn []
    [:div.main 
     [:div
      [:h1 "Citron File Manager"]
      (when-let [err (:error @db/app-state)]
        [:div.error err])
      (if (:user @db/app-state)
        [user-page]
        [:div
         [:div "Please login"]
         [:div
          [:input {:type :text :value (:username @db/login-store) :placeholder "Username" :on-change (set-login-value :username)}]
          [:input {:type :password :value (:password @db/login-store) :on-change (set-login-value :password)}]]
         [:input {:type :button :on-click login :value "Login"}]])]]))
