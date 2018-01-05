(ns quinto.html
  (:require [com.rpl.specter :refer [select ALL LAST FIRST transform]]
            [cljs.core.async :refer [chan <! put!]]
            [reagent.core :as r]
            [quinto.grid :as g]
            [quinto.mode :as m]
            [quinto.utils :refer [remove-item]])
  (:require-macros [cljs.core.async :refer [go-loop]]))

;;; Helper functions

(defn can-go-back? [state]
  (not= (get-in state [:mode :mode/type]) :default))

(defn can-confirm-move? [state]
  (and (= (get-in state [:mode :mode/type]) :assembling-move)
       (g/is-grid-valid? (state :grid))
       (> (count (get-in state [:mode :move-so-far]))
          0)))

(defn can-select-a-tile? [state]
  (some? (get-in state [:mode :selected-cell])))

;;; HTML rendering

(defn draw-cell [game-event-chan state grid x y cell-attributes]
  (let [cell (get-in grid [x y])
        cell-attributes (or cell-attributes #{})
        mode (state :mode)
        cell-class (str "cell "
                        (if (nil? cell)
                          "empty "
                          "full ")
                        (when (cell-attributes :blocked)
                          "blocked ")
                        (when (cell-attributes :playable)
                          "playable ")
                        (when (cell-attributes :historical-move-cell)
                          "historical-move-cell ")
                        (when (contains? (set (select [ALL FIRST]
                                                      (state :most-recent-computer-move)))
                                         [x y])
                          "just-played ")
                        (when (contains? (set (select [ALL FIRST]
                                                      (mode :move-so-far)))
                                         [x y])
                          "speculative ")
                        (when (cell-attributes :selected)
                          "selected "))]

    [:div
     {:class    cell-class
      :on-click (when (cell-attributes :playable)
                  #(put! game-event-chan
                         {:event/type :select-cell
                          :cell       [x y]}))}
     (if (nil? cell)
       ""
       cell)]))

(defn draw-grid [game-event-chan state grid cell-attributes-map]
  [:div#grid
   (for [x (range (count grid))]
     ^{:key x}
     [:div.column

      (for [y (range (count (grid x)))]
        ^{:key y}
        [draw-cell game-event-chan state grid x y (cell-attributes-map [x y])])])])

(defn draw-ghost-grid [grid move optimal-move]
  ; ok okokok

  ; if you made an optimal move,
  ; :mode :optimal-move will be nil
  ; just draw :mode :move on the ghost board in 100% opacity green

  ; if you didn't make an optimal move
  ; draw your non-optimal move on the regular board in orange
  ; draw your optimal move on the ghost board in green, 30% opacity
  (let [relevant-cell-map (into {}
                                (or optimal-move move))]
    (js/console.log "SUP" relevant-cell-map)

    [:div#ghost-grid
     (for [x (range (count grid))]
       ^{:key x}
       [:div.column

        (for [y (range (count (grid x)))]
          (let [cell (relevant-cell-map [x y])
                cell-class (if (contains? relevant-cell-map [x y])
                             (if optimal-move
                               "ghostly"
                               ; xxxx full-bodied is noop, remove
                               "full-bodied")
                             "hidden")]
            ^{:key y} [:div.cell
                       {:class cell-class}
                       (if (nil? cell)
                         ""
                         cell)]))])]))

(defn draw-tile [game-event-chan state value mode]
  [:div.tile
   {:class    (when-not (mode :selected-cell)
                "inactive")
    :on-click (when (can-select-a-tile? state)
                #(put! game-event-chan
                       {:event/type :select-tile
                        :value      value}))}
   value])

(defn draw-controls [state hand game-event-chan]
  (let [mode (state :mode)
        confirm-button-active (can-confirm-move? state)]
    [:div#controls
     {:class (when (mode :selected-cell)
               "assembling-move")}

     [:div#hand
      (for [[index value] (map-indexed vector hand)]
        ^{:key index} [draw-tile game-event-chan state value mode])]

     [:div.button.confirm
      {:class    (when (not confirm-button-active)
                   "inactive")
       :on-click #(when confirm-button-active
                    (put! game-event-chan {:event/type :confirm-move}))}
      "✔"]

     [:div.button.back
      {:class    (when-not (can-go-back? state)
                   "inactive ")
       :on-click #(when (can-go-back? state)
                    (put! game-event-chan {:event/type :go-back}))}
      "◀"]

     [:div.button.cancel
      {:class    (when (= (mode :mode/type) :default)
                   "inactive ")
       :on-click #(when (= (mode :mode/type) :assembling-move)
                    (put! game-event-chan
                          {:event/type :cancel-mode}))}
      "✖"]]))

(defn draw-scores [scores mode whose-score game-event-chan]
  (let [tentative-score (mode :tentative-score)
        DUMMY-SCORE {:value 0}
        scores (if (and (not (seq scores))
                        (not tentative-score))
                 [DUMMY-SCORE]
                 scores)]
    [:div.scores
     {:on-mouse-out #(when (= (mode :mode/type) :viewing-historical-move)
                       (put! game-event-chan {:event/type :stop-viewing-move}))}
     [:h3 whose-score]
     [:ul
      (for [[index score] (map-indexed vector scores)]
        ^{:key index} [:li
                       {:class         (str (when (and (= index (dec (count scores)))
                                                       (not= scores [DUMMY-SCORE])
                                                       (= whose-score "Computer"))
                                              "most-recent-score ")
                                            (when (score :was-optimal)
                                              "optimal "))
                        :on-mouse-over #(when (and (not= score DUMMY-SCORE)
                                                   (not= (mode :mode/type) :viewing-historical-move))
                                          (put! game-event-chan {:event/type   :view-move
                                                                 :grid         (score :grid)
                                                                 :move         (score :move)
                                                                 :optimal-move (if (= whose-score "Computer")
                                                                                 (score :move)
                                                                                 (score :optimal-move))}))}
                       (score :value)])

      (when (and tentative-score
                 (= whose-score "Player"))
        [:li.tentative-score
         (when (and (g/is-move-valid? (mode :original-grid)
                                      (mode :move-so-far))
                    (> tentative-score 0))
           {:class "valid"})
         tentative-score])]

     (when (> (count scores) 1)
       [:hr])
     (when (> (count scores) 1)
       [:p (apply + (map :value scores))])]))

(defn assemble-cell-attributes-map
  [state]
  (if (= (get-in @state [:mode :mode/type]) :viewing-historical-move)
    (into {} (map #(vector (first %) #{:historical-move-cell})
                  (get-in @state [:mode :move])))

    (let [playable-cells (set
                           (if (= (get-in @state [:mode :mode/type]) :default)
                             (g/find-playable-cells (@state :grid))
                             (get-in @state [:mode :available-cells])))
          blocked-cells (set (g/find-blocked-cells (@state :grid)))]
      (merge-with into {}
                  (map #(vector % #{:playable}) playable-cells)
                  (map #(vector % #{:blocked}) blocked-cells)
                  {(get-in @state [:mode :selected-cell]) #{:selected}}))))

(defn draw-game [state game-event-chan]
  [:div.game
   [draw-controls @state (@state :player-hand) game-event-chan]

   [:div.board-container
    [draw-scores (@state :player-scores) (@state :mode) "Player" game-event-chan]


    [draw-grid
     game-event-chan
     @state
     (@state :grid)
     (assemble-cell-attributes-map state)]

    [draw-ghost-grid
     (@state :grid)
     (get-in @state [:mode :move])
     (get-in @state [:mode :optimal-move])]

    [draw-scores (@state :ai-scores) (@state :mode) "Computer" game-event-chan]]])

;;; Event handling

(defn handle-game-events [state game-event-chan]
  (go-loop []
    (let [event (<! game-event-chan)]
      (js/console.log event)
      (condp = (event :event/type)
        :select-cell (if (= (get-in @state [:mode :mode/type]) :default)
                       (swap! state m/enter-assembling-move-mode (event :cell))
                       (swap! state m/select-cell (event :cell)))
        :select-tile (swap! state m/select-tile (event :value))
        :confirm-move (swap! state m/confirm-move)
        :go-back (swap! state m/go-back)
        :cancel-mode (swap! state m/cancel-mode)
        :view-move (swap! state m/view-historical-move (event :grid) (event :move) (event :optimal-move))
        :stop-viewing-move (swap! state m/stop-viewing-historical-move)
        nil))
    (recur)))

; Atom used for removing preexisting event handlers when fighweel reloads our code.
(defonce keyup-handler (atom nil))

(def ESCAPE-KEY-CODE 27)
(def LEFT-ARROW-KEY-CODE 37)
(def ENTER-KEY-CODE 13)
(def ZERO-KEY-CODE 48)
(def NUMBER-KEY-CODES {49 1
                       50 2
                       51 3
                       52 4
                       53 5})

;;; Public API

(defn render-game [state]
  (when @keyup-handler
    (.removeEventListener js/document "keyup" @keyup-handler))

  (let [game-event-chan (chan)
        key-handler (fn [event]
                      (let [key-code (.-keyCode event)

                            game-event (condp contains? key-code
                                         #{ESCAPE-KEY-CODE} {:event/type :cancel-mode}

                                         #{LEFT-ARROW-KEY-CODE} (when (can-go-back? @state)
                                                                  {:event/type :go-back})

                                         #{ENTER-KEY-CODE} (when (can-confirm-move? @state)
                                                             {:event/type :confirm-move})


                                         #{ZERO-KEY-CODE} (let [textarea (js/document.createElement "textarea")]
                                                            (set! (.-value textarea)
                                                                  (str "My Quinto game's state is: " (pr-str @state)))
                                                            (.appendChild js/document.body textarea)
                                                            (.select textarea)
                                                            (js/document.execCommand "copy")
                                                            (.removeChild js/document.body textarea))

                                         NUMBER-KEY-CODES (when (can-select-a-tile? @state)
                                                            (let [hand (@state :player-hand)
                                                                  hand-index (dec (NUMBER-KEY-CODES key-code))]
                                                              (when (< hand-index (count hand))
                                                                {:event/type :select-tile
                                                                 :value      (nth hand hand-index)})))
                                         nil)]

                        (when game-event
                          (put! game-event-chan game-event))))]

    (r/render-component [draw-game state game-event-chan]
                        (js/document.getElementById "app"))

    ; Back out of modes if the user hits the escape key.
    (.addEventListener js/document "keyup" key-handler)

    (.addEventListener js/document "keydown" #(when (= (.-keyCode %)
                                                       LEFT-ARROW-KEY-CODE)
                                                (.preventDefault %)))
    (reset! keyup-handler key-handler)

    (handle-game-events state game-event-chan)))
