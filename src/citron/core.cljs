(ns citron.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [citron.views.home :refer [user-page login-page]]
   [citron.http :as http]
   [citron.db :as db]
   [citron.utils :as utils]

   [clojure.string :as s]
   [accountant.core :as a]   
   [secretary.core :as secretary :refer-macros [defroute]]
   [reagent.core :as r]
   [taoensso.timbre :as t]
   [cljs.core.async :refer [timeout <!]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import
   [goog.history Html5History]
   [goog History Uri]))

(secretary/set-config! :prefix "#")

(defn make-nonce []
  (str (.getTime (js/Date.))))

(enable-console-print!)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config and states

(defn page []
  [:div.main
   (when-let [err (:error @db/app-state)]
     [:div.error {:on-click db/clear-error} err])
   [@db/page-store]
   (when (:page-loading @db/app-state) 
     [:div.popup.popup--open
      [:div.pagespinner
       [:div.lds-dual-ring]]])])

(defroute "/" [] (if (:user @db/app-state)
                   (a/navigate! "#/user")
                   (a/navigate! "#/login")))

(defroute "/login" [] (if (:user @db/app-state)
                        (a/navigate! "#/user")
                        (reset! db/page-store login-page)))

(defroute "/user" [query-params]
  (t/info "query-params" query-params)
  (if (:user @db/app-state)
    (let [{:keys [path offset filter-term]} query-params]
      (swap! db/app-state assoc :filter-term filter-term :filter? (not (s/blank? filter-term)))
      (http/get-file path (if (s/blank? offset) 0 (js/parseInt offset)) filter-term)
      (reset! db/page-store user-page))
    (a/navigate! "#/login")))

(defn mount-components []
  (r/render [#'page] (.getElementById js/document "app")))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))



;; (defn hook-browser-navigation! []
;;   (let [history (doto (Html5History.)
;;                   (events/listen
;;                    EventType/NAVIGATE
;;                    (fn [event]
;;                      (secretary/dispatch! (.-token event))))
;;                   (.setUseFragment true)
;;                   (.setPathPrefix "#")
;;                   (.setEnabled true))]

;;     (events/listen js/document "click"
;;                    (fn [e]
;;                      (. e preventDefault)
;;                      (let [path (.getPath (.parse Uri (.-href (.-target e))))
;;                            title (.-title (.-target e))]
;;                        (when path
;;                          (. history (setToken path title))))))))

;; https://lfn3.net/2019/01/07/re-frame-and-the-back-button/
;; (defn disable-context-menu! []
;;   (set! (.-oncontextmenu js/window)
;;         (fn [evt]
;;           (.preventDefault evt)
;;           (.stopPropagation evt)
;;           false)))

(defn start-msg-loader []
  (go-loop []
    (try
      (when (and (:user @db/app-state)
                 (not (:new-msg @db/app-state)))
        (http/get-msg-board))
      (catch :default e
        (t/error "msg-loader error" e)))
    (<! (timeout 5000))
    (recur)))

(defn start-time-udpater []
  (go-loop []
    (let [now (utils/long->complex-string)]
      (when-not (= now (:now-as-string @db/timed-store))
        (db/update-timed-store :now-as-string now)))
    (<! (timeout 499))
    (recur)))

(defn configure-pop-state! []
  ;; (set! (.-onpopstate js/window) handle-pop-state)
  (.pushState js/history nil nil (.-URL js/document))
  (.addEventListener js/window
                     "popstate"
                     (fn []
                       (let [url (.-URL js/document)
                             uri (or (utils/url-to-uri url) "#/")]
                         (.pushState js/history nil nil url)
                         ;; use this to make back button work
                         (a/navigate! uri)))))

(defonce init!
  (delay
    ;; (disable-context-menu!)
    (a/configure-navigation!
     {:nav-handler
      (fn [path]
        (secretary/dispatch! path))
      :path-exists?
      (fn [path]
        (secretary/locate-route path))})
    (a/dispatch-current!)
    (start-msg-loader)
    (start-time-udpater)
    (configure-pop-state!)
    (hook-browser-navigation!)))

(enable-console-print!)

(defn ^:export main []
  @init!
  (mount-components))

(main)

