(ns citron.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [citron.views.home :refer [index-page]]
   [citron.http :as http]
   [citron.db :as db]
   
   [ajax.core :refer [PUT GET DELETE json-response-format json-request-format]]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [reagent.session :as session]
   [clojure.string :as s]
   [taoensso.timbre :as t]

   [cljs.reader :as reader]
   [cljs.core.async :refer [timeout <!]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import
   [goog History]))


(defn make-nonce []
  (str (.getTime (js/Date.))))

(enable-console-print!)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config and states

(def pages
  {:index index-page})

(defn page []
  (if-let [pg (session/get :page)]
    [(pages pg)]
    [(pages :index)]))

;; todo page title can be set by watching session/state
(defroute "/" [] (session/put! :page :index))

(defroute "/radio-list" [] (session/put! :page :radio-list))

(defn mount-components []
  (r/render [#'page] (.getElementById js/document "app")))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


(defn disable-context-menu! []
  (set! (.-oncontextmenu js/window)
        (fn [evt]
          (.preventDefault evt)
          (.stopPropagation evt)
          false)))

(defn start-msg-loader []
  (go-loop []
    (try
      (when (and (:user @db/app-state)
                 (not (:edit-msg-board @db/app-state)))
        (http/get-msg-board))
      (catch :default e
        (t/error "msg-loader error" e)))
    (<! (timeout 5000))
    (recur)))

(defonce init!
  (delay
    (disable-context-menu!)
    (start-msg-loader)
    (hook-browser-navigation!)))

(enable-console-print!)

(defn ^:export main []
  @init!
  (mount-components))

(main)

