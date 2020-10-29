(ns citron.db
  (:require
   [taoensso.timbre :as t]
   [goog.object :as gobj]
   [clojure.core.async :as a]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [clojure.string :as s]))

(def hidden-audio-player-id "hidden-audioplayer")

;; current page
(defonce page-store (atom nil))

;; global app state
(defonce app-state (local-storage (atom {:file-offset 0}) :app-state))

;; login cred state
(defonce login-store (atom {}))

;; upload file state
(defonce tmp-file-store (atom {}))

;; current file/dir state
(defonce file-store (atom nil))

;; message board 
(defonce msg-store (local-storage (atom []) :msg-store))

;; temp msg on edit
(defonce temp-msg-store (local-storage (atom "") :temp-msg-store))

;; data updated periodically
(defonce timed-store (atom {}))

;; playback config
(defonce playback-config-store (atom {:speed 1.0}))

;; floating menu
(defonce floatingmenu-state (atom {:show? false}))

(defonce audioplayer-state (local-storage (atom {:playlist []
                                                 :shuffle? false
                                                 :paused? true
                                                 :id 0 ;; current playing id
                                                 :prev-id 0
                                                 :expanded? false})
                                          :audioplayer-state))

(defn- set-active-speed [speed]
  (when-let [player (.querySelector js/document ".file__mediaplayer")]
    (t/info "Set playbackRate to" speed)
    (gobj/set player "playbackRate" speed)))

(defn set-playback-speed [speed]
  (swap! playback-config-store assoc :speed speed :expanded? false)
  (set-active-speed speed))

(defn reset-playback-speed []
  (swap! playback-config-store assoc :speed 1.0 :expanded? false)
  (set-active-speed 1.0))

(defn clear-error []
  (swap! app-state dissoc :error :error-level))

(defn set-error
  "Set error message, with optional timeout. After timeout, message will
  be cleared automatically."
  ([msg]
   (set-error msg 0))
  ([msg ttl]
   (set-error msg ttl :error))
  ([msg ttl error-level]
   (swap! app-state assoc :error msg :error-level (name error-level))
   (when (and ttl (pos? ttl))
     (a/go
       (a/<! (a/timeout ttl))
       (clear-error)))))

(defn update-timed-store [ky vl]
  (swap! timed-store assoc ky vl))

