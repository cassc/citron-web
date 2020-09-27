(ns citron.db
  (:require

   [taoensso.timbre :as t]
   [secretary.core :as secretary :refer-macros [defroute]]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r :refer [atom]]
   [clojure.string :as s]))

;; global app state
(defonce app-state (atom {}))

;; login cred state
(defonce login-store (atom {}))

;; upload file state
(defonce tmp-file-store (atom {}))

;; current file/dir state
(defonce file-store (atom nil))

(defonce msg-store (atom ""))
