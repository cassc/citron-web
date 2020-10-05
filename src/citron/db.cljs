(ns citron.db
  (:require
   [taoensso.timbre :as t]
   [goog.object :as gobj]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [clojure.string :as s]))

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

(defn- set-active-speed [speed]
  (when-let [player (or
                     (.querySelector js/document "audio")
                     (.querySelector js/document "video"))]
    (gobj/set player "playbackRate" speed)))

(defn set-playback-speed [speed]
  (swap! playback-config-store assoc :speed speed :expanded? false)
  (set-active-speed speed))

(defn reset-playback-speed []
  (swap! playback-config-store assoc :speed 1.0 :expanded? false)
  (set-active-speed 1.0))

(defn clear-error []
  (swap! app-state dissoc :error))

(defn update-timed-store [ky vl]
  (swap! timed-store assoc ky vl))

