(ns citron.views.home
  (:require
   [citron.db :as db]
   [citron.http :as http]
   [citron.utils :as utils]

   [alandipert.storage-atom :refer [local-storage]]
   [clojure.string :as s]
   [taoensso.timbre :as t]))

(def app-title "Citron File Manager")

(def page-size 50)

(defn parent [path]
  (s/join "/" (butlast (s/split path #"/"))))

(defn top? [path]
  (#{"." "./" ""} path))

(defn set-login-value [key]
  (fn [e]
    (swap! db/login-store assoc key (-> e .-target .-value))))

(defn to-display-path
  ([parent path]
   (subs path (inc (count parent))))
  ([path]
   (s/replace (or path "") #"^\./" "")))

(defn login []
  (when-not (some s/blank? (vals @db/login-store))
    (http/login @db/login-store)))

(defn goto-file [{:keys [path]}]
  (http/get-file path))

(defn goto-parent [{:keys [path]}]
  (goto-file {:path (parent path)}))

(defn edit-msg-board []
  (let [{:keys [edit-msg-board]} (swap! db/app-state update :edit-msg-board not)]
    (if edit-msg-board
      (swap! db/app-state assoc :hide-msg-content nil)
      (http/put-msg-board @db/msg-store))))

(defn- toggle-hidden-msg-content []
  (when-not (:edit-msg-board @db/app-state)
    (swap! db/app-state update :hide-msg-content not)))

(defn- msg-panel []
  (fn []
    [:div.mboard
     [:div.mboard__btn
      [:div.mboard__title
       [:span.mboard__title-title {:on-click toggle-hidden-msg-content}
        (if (:hide-msg-content @db/app-state)
          [:i.mdi-message-bulleted-off.mdi]
          [:i.mdi-message-reply-text.mdi])
        " Message board"]
       [:input.btn.mboard__btn-edit
        {:class (if (:edit-msg-board @db/app-state)
                  "mboard__btn-edit--editing"
                  "")
         :type :button
         :value (if (:edit-msg-board @db/app-state)
                  "Save"
                  "Edit")
         :on-click edit-msg-board}]]]
     (if (:edit-msg-board @db/app-state)
       [:textarea {:value @db/msg-store :on-change (fn [e] (reset! db/msg-store (-> e .-target .-value)))}]
       (when-not (:hide-msg-content @db/app-state)
         [:pre.mboard__content @db/msg-store]))]))

(defn make-static-path [path]
  (str "/static/file?path=" path))

(defn- filelist [path files]
  [:div.file__files
   (let [parent path]
     (doall
      (for [{:keys [isdir path mime size] :as f} files]
        [:div.file__filewrapper {:key path :on-click #(goto-file f)}
         [:div.file__filedetails
          [:i.file__fileicon.mdi
           {:class (let [mime (or mime "")]
                     (cond 
                       isdir "mdi-folder"
                       (s/includes? mime "image") "mdi-image"
                       (s/includes? mime "text") "mdi-file-document"
                       :else "mdi-format-page-break"))}]
          [:div.file__filename (to-display-path parent path)]]
         [:div.file__filemeta
          [:div]
          [:div (utils/readable-filesize size)]]])))])

(defn- file-panel []
  (fn []
    (let [{:keys [more path files isdir content mime] :as f} @db/file-store]
      [:div.file
       [:div.file__title
        [:span.file__title--home {:on-click #(goto-file {:path "."})}
         [:i.mdi-home.mdi]]
        [:span.file__title-path (str (when-not (= "." path)
                                       (to-display-path path)))]]
       [:div.file__btns
        (when-not (top? path)
          [:a.btn {:href "javascript:;"
                   :on-click #(goto-parent f)}
           [:i {:class "mdi mdi-arrow-up"}]
           "Back"])
        (when-not (and isdir (pos? (count files)))
          [:a.btn {:href "javascript:;"
                   :on-click #(http/delete-file f (fn [] (goto-parent f)))}
           [:i {:class "mdi mdi-delete-circle"}]
           "Delete"])
        (when-not isdir
          [:a.btn {:href (make-static-path path) :target "_blank"}
           [:i {:class "mdi mdi-eye"}]
           "Open"])
        (when isdir
          [:div
           [:label.btn {:for "fileuploader"}
            [:i {:class "mdi mdi-briefcase-upload"}]
            "Upload"]
           [:input#fileuploader
            {:type :file
             :on-change (fn [e]
                          (let [file (first (array-seq (.. e -target -files)))
                                filename (when file (.-name file))]
                            (t/info "uploading file" filename)
                            (when filename
                              (http/upload-file
                               (assoc @db/tmp-file-store :file file :parent path)
                               (fn []
                                 (http/get-file path))))))}]])]
       (when content
         [:div.file__content
          [:pre content]])
       (when (s/includes? (or mime "") "image")
         [:div.file__imgpreview
          [:img {:src (make-static-path path)}]])
       [filelist path files]
       (when more
         [:div.file__morebtn [:a.btn {:href "javascript:;"} "More"]])])))

(defn- user-page []
  (fn []
    [:div.user
     [:div.user__title
      [:div.user__title-left (:now-as-string @db/timed-store)]
      [:div.user__title-center app-title]
      [:div.user__title-right]]
     [:div.user__panel
      [msg-panel]
      [file-panel]]]))

(defn login-page []
  (fn []
    [:div.login
     [:h1 app-title]
     [:div
      [:div.login__title "Please login"]
      [:div.login__fields
       [:input {:auto-complete "off" :type :text :value (:username @db/login-store) :placeholder "Username" :on-change (set-login-value :username)}]
       [:input {:type :password :placeholder "Password" :value (:password @db/login-store) :on-change (set-login-value :password)}]]
      [:input {:type :button :on-click login :value "Login"}]]]))

(defn index-page []
  (fn []
    [:div.main
     (when-let [err (:error @db/app-state)]
       [:div.error {:on-click db/clear-error} err])
     (if (:user @db/app-state)
       [user-page]
       [login-page])]))
