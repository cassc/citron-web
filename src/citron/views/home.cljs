(ns citron.views.home
  (:require
   [citron.db :as db]
   [citron.http :as http]
   [citron.utils :as utils]
   [accountant.core :as a]
   [clojure.string :as s]
   [taoensso.timbre :as t]))

(def app-title "Citron")

(def page-size 100)

(defn parent [path]
  (s/join "/" (butlast (s/split path #"/"))))

(defn top? [path]
  (#{"." "./" ""} path))

(defn set-login-value [key]
  (fn [e]
    (swap! db/login-store assoc key (-> e .-target .-value))))

(defn set-app-state-value
  ([key]
   (set-app-state-value key nil))
  ([key on-done]
   (fn [e]
     (swap! db/app-state assoc key (-> e .-target .-value))
     (when on-done
       (on-done)))))

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

(defn cancel-add-msg []
  (swap! db/app-state dissoc :hide-msg-content :new-msg)
  (reset! db/temp-msg-store ""))

(defn edit-msg-board []
  (let [{:keys [new-msg]} (swap! db/app-state update :new-msg not)]
    (if new-msg
      (swap! db/app-state assoc :hide-msg-content nil)
      (when-not (s/blank? @db/temp-msg-store)
        (http/put-msg-board @db/temp-msg-store)))))

(defn del-msg-board []
  (swap! db/app-state update :del-msg not))

(defn- toggle-hidden-msg-content []
  (when-not (:new-msg @db/app-state)
    (swap! db/app-state update :hide-msg-content not)))

(defn- toggle-rename-file [filename]
  (fn [_]
    (let [{:keys [rename-file?]} (swap! db/app-state update :rename-file? not)]
      (swap! db/app-state assoc :filename (when rename-file? filename)))))

(defn- toggle-file-filter [_]
  (let [{:keys [filter?]} (swap! db/app-state update :filter? not)]
    (when-not filter?
      (a/navigate! "#/user" {:path (:path @db/file-store)
                            :offset 0}))))

(defn- save-filename [path]
  (fn [_]
    (http/rename-file path (:filename @db/app-state))))

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
       (when-not (:del-msg @db/app-state)
         (if (:new-msg @db/app-state)
           (when-not (s/blank? @db/temp-msg-store)
             [:input.btn.mboard__btn-edit
              {:class "mboard__btn-edit--editing"
               :type :button
               :value "Save"
               :on-click edit-msg-board}])
           [:input.btn.mboard__btn-edit
            {:type :button
             :value "Add"
             :on-click edit-msg-board}]))
       (when (:new-msg @db/app-state)
         [:input.btn.mboard__btn-edit
          {:type :button
           :value "Cancel"
           :on-click cancel-add-msg}])
       (when-not (:new-msg @db/app-state)
         [:input.btn.mboard__btn-edit
          {:class (if (:del-msg @db/app-state)
                    "mboard__btn-edit--editing"
                    "")
           :type :button
           :value (if (:del-msg @db/app-state)
                    "Ok"
                    "Delete")
           :on-click del-msg-board}])]]
     (if (:new-msg @db/app-state)
       [:textarea {:value @db/temp-msg-store :on-change (fn [e] (reset! db/temp-msg-store (-> e .-target .-value)))}]
       (when-not (:hide-msg-content @db/app-state)
         (doall
          (for [{:keys [id msg]} @db/msg-store]
            [:div.mboard__content {:key id}
             (when (:del-msg @db/app-state)
               [:i.mdi.mdi-delete-forever {:on-click #(http/delete-msg id)}])
             [:pre msg]]))))]))

(defn make-static-path [path]
  (str "/staticfile/" path))

(defn- filelist [path files]
  [:div.file__files
   (let [parent path]
     (doall
      (for [{:keys [isdir path mime size]} files]
        [:div.file__filewrapper {:key path :on-click #(a/navigate! "#/user" {:path path})}
         [:div.file__filedetails
          [:i.file__fileicon.mdi
           {:class (let [mime (or mime "")]
                     (cond 
                       isdir "mdi-folder"
                       (s/includes? mime "image") "mdi-image"
                       (s/includes? mime "text") "mdi-file-document"
                       :else "mdi-format-page-break"))}]
          [:div.file__filename (to-display-path parent path)]]
         (when (s/includes? (or mime "") "image")
           [:div.file__imgthumbnail [:img {:src (make-static-path path)}]])
         [:div.file__filemeta
          [:div]
          [:div (utils/readable-filesize size)]]])))])

(defn- file-panel []
  (fn []
    (let [{:keys [more path files isdir content mime offset total] :as f} @db/file-store]
      [:div.file
       [:div.file__title
        [:span.file__title--home {:on-click #(goto-file {:path "."})}
         [:i.mdi-home.mdi]]
        [:span.file__title-path (str (when-not (= "." path)
                                       (to-display-path path)))]]
       [:div.file__btns
        (when-not (or (:rename-file? @db/app-state) (top? path))
          [:a.btn {:href "javascript:;"
                   :on-click #(a/navigate! "#/user" {:path (parent path)})}
           [:i {:class "mdi mdi-arrow-up"}]
           "Up"])
        (when-not (or (:rename-file? @db/app-state) (and isdir (pos? (count files))))
          [:a.btn {:href "javascript:;"
                   :on-click #(http/delete-file f (fn [] (goto-parent f)))}
           [:i {:class "mdi mdi-delete-circle"}]
           "Delete"])
        (when-not (or (:rename-file? @db/app-state) isdir)
          [:a.btn {:href "javascript:;" :on-click (toggle-rename-file (utils/to-filename path))}
           [:i {:class "mdi mdi-rename-box"}]
           "Rename"])
        (when-not (or (:rename-file? @db/app-state) isdir)
          [:a.btn {:href (make-static-path path) :target "_blank"}
           [:i {:class "mdi mdi-eye"}]
           "Download"])
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
                                 (http/get-file path))))))}]])
        (when isdir
          [:a.btn {:href "javascript:;" :on-click toggle-file-filter
                   :class (when (:filter? @db/app-state)
                            "file__filter--active")}
           [:i.mdi.mdi-filter-outline]
           "Filter"])]
       (when (and isdir (:filter? @db/app-state))
         [:div.file__filter-input
          [:input {:type :text :value (:filter-term @db/app-state)
                   :placeholder "Search"
                   :on-change (set-app-state-value :filter-term)
                   :on-key-press (fn [e]
                                   (when (= 13 (.-charCode e))
                                     (a/navigate! "#/user" {:path path :filter-term (:filter-term @db/app-state "")})))}]
          [:a.btn [:i.mdi.mdi-keyboard-return
                   {:href "javascript:;"
                    :on-click #(a/navigate! "#/user" {:path path :filter-term (:filter-term @db/app-state "")})}]
           " Go"]])
       (when (:rename-file? @db/app-state)
         [:div
          [:div.bold "Rename file"]
          [:div.file__renametitle
           [:div (utils/to-filename path)]
           [:i.mdi.mdi-arrow-right]
           [:input
            {:type :text
             :value (:filename @db/app-state)
             :on-change (set-app-state-value :filename)}]
           [:a.btn {:href "javascript:;"
                    :class (when (not= (:filename @db/app-state) (utils/to-filename path))
                             "file__renametitlebtn--active")
                    :on-click (save-filename path)}
            "Save"]
           [:a.btn {:href "javascript:;" :on-click (toggle-rename-file (utils/to-filename path))} "Cancel"]]])
       (when content
         [:div.file__content
          [:pre content]])
       (when (s/includes? (or mime "") "image")
         [:div.file__imgpreview
          [:img {:src (make-static-path path)}]])
       (when (s/includes? (or mime "") "audio")
         [:div.file__imgpreview
          [:audio.file__mediaplayer {:controls true :src (make-static-path path)}]])
       (when (s/includes? (or mime "") "video")
         [:div.file__imgpreview
          [:video.file__mediaplayer {:controls true :src (make-static-path path)}]
          (if (:expanded? @db/playback-config-store)
            [:div.file__mediaplayer-speedcontrol
             [:div "Speed: "]
             (doall
              (for [speed (take 11 (iterate (partial + 0.25) 0.5))]
                [:div.file__mediaplayer-speed {:key (str "playerspeed." speed)
                                               :class (when (= speed (:speed @db/playback-config-store))
                                                        "file__mediaplayer-speed--active")
                                               :on-click #(db/set-playback-speed speed)}  
                 (str speed "X")]))]
            [:div.file__mediaplayer-speedcontrol
             [:div "Speed: "]
             [:div.file__mediaplayer-speed
              {:on-click #(swap! db/playback-config-store assoc :expanded? true)}
              (str (:speed @db/playback-config-store) "X")]])])
       [filelist path files]
       (when more
         [:div.file__morebtn
          [:span (str (- total offset page-size) " more items left. ")
           [:a.btn {:href "javascript:;" :on-click #(http/get-file path (+ offset page-size))}
            "Load"]]])])))

(defn user-page []
  (fn []
    [:div.user
     [:div.user__title.user__title--pc
      [:div.user__title-left (:now-as-string @db/timed-store)]
      [:div.user__title-center app-title]
      [:div.user__title-right]]
     [:div.user__title.user__title--phone
      [:div.user__title-center app-title]
      [:div.user__title-right (:now-as-string @db/timed-store)]]
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
      [:input.btn {:type :button :on-click login :value "Login"}]]]))


