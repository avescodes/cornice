(ns app-walkthrough.walkthrough)

;; Adapted from the Cognitect pedestal-app walkthrough

;; In Pedestal, applications receive input as inform messages. An
;; inform message is a vector of event-entries. Each event-entry has
;; the form

'[source event state(s)]

;; Inform messages may be received from back-end services or from the
;; user interface. For example, a button click may generate an inform
;; message like the one shown below.

[[[:ui :button :some-id] :click]]

;; This inform message has one event-entry. The source is

[:ui :button :some-id]

;; and the event is

:click

;; There are no states included in this message.

;; An application will need to have some code which knows what this
;; particular event means for the application. Messages which tell
;; other things to change are called transform messages. Each
;; transform message contains one or more transformations. A
;; transformation has the form

'[target op arg(s)]

;; For example, the value of a counter should be incremented when this
;; button is clicked. The tranform message which would cause this to
;; happen is shown below.

[[[:info :counter :some-id] inc]]

;; In this message, the target is

[:info :counter :some-id]

;; and the op is the `inc` function. This transformation has no
;; arguments.

;; Messages are conveyed on core.async channels.

(require '[clojure.core.async :refer [go chan <! >! map> put! alts!! timeout]])

;; Channels which convey infrom messages are called inform channels
;; and channels which convey transform messages are called transform
;; channels.

;; When an inform message is received, transform messages should be
;; generated which cause some part of the application to
;; change. To accomplish this we will need a function which receives
;; an inform message and produces a collection of transform messages.

(defn inc-counter-transform [inform-message]
  (let [[[source event]] inform-message
        [_ _ counter-id] source]
    [[[[:info :counter counter-id] inc]]]))

;; This function extracts the id of the counter to increment from the source
;; of the event-entry. For simplicilty this function assumes that it
;; will only have one event-entry in the inform message.

;; We need some way to map inform messages to this function and then
;; put the generated transform messages on a transform channel. That
;; is the purpose of the `io.pedestal.cornice.map` namespace.

(require '[io.pedestal.cornice.map :as app-map])

;; First we need to create a configuration that will describe how to
;; dispatch inform messages to functions. The following configuration
;; will dispatch inform messages from the source [:ui :button :*] with
;; a :click event to the function `inc-counter-transform`.

(def input-config [[inc-counter-transform [:ui :button :*] :click]])

;; This config is a vector of vectors and can contain any number of
;; vectors. Each vector can have any number or source event
;; pairs. Wildcards, :* and :**, can be used in the source path and :*
;; can be used to match any event. :* matches exactly one element and
;; :** matches 0 or more elements.

;; Now we will create a map that has an input inform channel and an
;; output transform channel and uses the above config.

;; Cornice's convention and construction is to create the output channel
;; first, and pass it into a function that returns the input channel for a
;; given component.  This allows you, the application author, to tell Pedestal
;; channel semantics that make sense for your application.
;; Let's give it a shot!

;; Create the transform channel (output)

(def transform-chan (chan 10))

;; Create the map passing the config and transform channel and
;; returning the inform channel (input).

(def inform-chan (app-map/inform->transforms input-config transform-chan))

;; We can now send an inform message on the inform channel

(put! inform-chan [[[:ui :button :a] :click]])

;; and see the transform message come out of the transform channel.

(println (first (alts!! [transform-chan (timeout 100)])))

;; Conceptually, this is like using core.async's `map>`.
;; Let's take a look at how we might manually achieve the same effect

(require '[io.pedestal.cornice.match :as app-match])

;; First, we'd need to build a dispatch map - a way to navigate and match
;; an inform message (ie: an event), and then dispatch the corresponding
;; transform function(s).
;; This is a common design pattern in functional programming.

(def inform-dispatch-index (app-match/index input-config))

;; Next, we need to establish an output channel for our transforms

(def another-transform-chan (chan 10))

;; We now want to wire an input channel, to our dispatch table,
;; potentially `match`, apply the corresponding transform function, placing
;; the result onto the transform channel

(def another-inform-chan (map> (fn [inf-mess]
                                 (let [[f patterns args] (first (app-match/match inform-dispatch-index inf-mess))]
                                   (f args)) )
                               another-transform-chan))

(put! another-inform-chan [[[:ui :button :*] :click]])
(println (first (alts!! [another-transform-chan (timeout 100)])))


;; Let's get back to using the built-in Cornice pieces.
;; So we now have a transform message which can be used to increment
;; a value in the information model.

;; To work with the information model we use the
;; `io.pedestal.cornice.model` namespace.

(require '[io.pedestal.cornice.model :as app-model])

;; A transform channel sends messages to the model and an inform
;; channel sends messages which describe changes to the model. We have
;; already seen an example of the transform message which is sent to
;; a model. What does a model inform message look like? An example is
;; shown below.

[[[:info :counter :a] :updated {:info {:counter {:a 0}}} {:info {:counter {:a 1}}}]]

;; The source is the path in the model which changed. The event is
;; either :added, :updated or :removed. The states are the entire
;; old and new model values.

;; To create a new model, we first create the inform channel (output).

(def model-inform-chan (chan 10))

;; We then call `transform->inform` passing the initial model value
;; and the inform channel. This returns the transform
;; channel (input).
;; Remember, for the model, transform messages go in and
;; inform messages come out.

(def model-transform-chan (app-model/transform->inform {:info {:counter {:a 0}}} model-inform-chan))

;; We can now send a transform message to the model

(put! model-transform-chan [[[:info :counter :a] inc]])

;; and get the inform message which describes the change to the model.

(println (first (alts!! [model-inform-chan (timeout 100)])))

;; When building an application, we will combine these parts. We can
;; create a pipeline of channels where input inform messages go in one
;; end and model inform messages come out of the other end.

;; We start with our output channel, our model inform message channel.
(def model-inform-chan (chan 10))

;; We get our input channel by threading together constructions.
(def input-inform-chan (->> model-inform-chan ;; [5]
                            (app-model/transform->inform {:info {:counter {:a 0}}}) ;; [3] [4]
                            (app-map/inform->transforms input-config))) ;; [1] [2]

;; Looking at the form above, you read it backwards (bottom to top):
;; An input/inform message passes through the config [1],
;; produces a transform message [2], that is passed to the model [3],
;; which produces an inform message on how it was modified [4].
;; Those messages are put on our model-inform-channel, our output [5].

;; As stated above, a pipeline of channels where input inform messages go in one
;; end and model inform messages come out of the other end.


;; We can now send a button click event on the input-inform-channel

(put! input-inform-chan [[[:ui :button :a] :click]])

;; and see the update to the model on the model-inform-channel

(println (first (alts!! [model-inform-chan (timeout 100)])))

;; So what should we do with model inform messages? Something in our
;; application will most likely need to change based on the changes to
;; the information model. Once again we need to generate transforms
;; based on inform messages. This time the transform message will go
;; out to the UI so that the counter value can be displayed.

(defn counter-text-transform [inform-message]
  (vector
   (mapv (fn [[source _ _ new-model]]
           (let [counter-id (last source)]
             [[:ui :counter-id counter-id] :set-value (get-in new-model source)]))
         inform-message)))

;; In the function above, one transformation is created for each
;; event-entry. If more than one counter is updated during a
;; transaction on the info model then a single transform will be sent
;; out will all the updates to the UI. This could also be written so
;; that one transform is generated for each change.

;; Again we create a configuration which will map inform messages to
;; this function

(def output-config [[counter-text-transform [:info :counter :*] :updated]])

;; We can then create a map from this config

(def output-transform-chan (chan 10))
(def output-inform-chan (app-map/inform->transforms output-config output-transform-chan))

;; and send a model inform message to test it out

(put! output-inform-chan [[[:info :counter :a] :updated
                           {:info {:counter {:a 0}}}
                           {:info {:counter {:a 1}}}]])

(println (first (alts!! [output-transform-chan (timeout 100)])))

;; Now let's put it all together. Create the transform channel that
;; will send transform messages to the UI (the output, the last step, the sink).

(def output-transform-chan (chan 10))

;; Build the pipeline that includes the model and the input and output
;; maps. This returns the inform channel that you will give to UI
;; componenets or anything which will send input to the application
;; (the input, the first step, the source).

(def input-inform-chan (->> output-transform-chan
                            (app-map/inform->transforms output-config)
                            (app-model/transform->inform {:info {:counter {:a 0}}})
                            (app-map/inform->transforms input-config)))

;; Send the button click event on the input channel.
;; ---------------------------------------------------
;;     The click will inc the model, the model will inform of the change,
;;     and a transform will be generated to update the counter UI to 1

(put! input-inform-chan [[[:ui :button :a] :click]])

;; Consume UI transforms from the output channel.
;; -----------------------------------------------
;;     So we expect to see a transform message for the UI, setting the counter to 1.

(println (first (alts!! [output-transform-chan (timeout 100)])))

;; Try sending the :click event several times.

;; This is the basic application loop.

;; What if we had two counters and we wanted to have a third counter
;; which was always the sum of the other two? The
;; `io.pedestal.cornice.flow` namespace provides dataflow capabilities.

(require '[io.pedestal.cornice.flow :as app-flow])

;; Create a function that turns info model informs into info model transforms.

(defn sum-transform [inform-message]
  (let [[[_ _ _ new-model]] inform-message]
    [[[[:info :counter :c] (constantly (+ (get-in new-model [:info :counter :a])
                                          (get-in new-model [:info :counter :b])))]]]))

;; Create the flow config.
;; ------------------------
;;     Remember a config describes how dispatch messages to a specific function,
;;     based on a series of inform messages.  When our :a or :b counter are updated,
;;     (our source event pairs), we need to `sum-transform`

(def flow-config [[sum-transform [:info :counter :a] :updated [:info :counter :b] :updated]])

;; Call the `flow/transform->inform` function instead of
;; `model/transform->inform`. This function takes an additional config argument.

(def output-transform-chan (chan 10))
(def input-inform-chan
  (->> output-transform-chan
       (app-map/inform->transforms output-config)
       (app-flow/transform->inform {:info {:counter {:a 0 :b 0 :c 0}}} flow-config)
       (app-map/inform->transforms input-config)))

;; Send inform messages as if two buttons where clicked.

(put! input-inform-chan [[[:ui :button :a] :click]])
(put! input-inform-chan [[[:ui :button :b] :click]])

;; Read two inform messages from the output channel.

(println (first (alts!! [output-transform-chan (timeout 100)])))
(println (first (alts!! [output-transform-chan (timeout 100)])))


;; We sometimes need to route messages from one transform channel to
;; one or more other transform channels.  For example, we may need
;; to update the UI and send some data back to the server.

;; For this we use the `io.pedestal.cornice.route` namespace.

(require '[io.pedestal.cornice.route :as app-route])

;; Suppose we have three different channels on which we would like to
;; send transform messages.

;; One for UI transforms,

(def ui-transform-chan (chan 10))

;; one for transforms to the info model,

(def info-transform-chan (chan 10))

;; and one for service transforms.

(def service-transform-chan (chan 10))

;; We can create a router which will route messages from an incoming
;; transform channel to one of these channels. First we create the
;; transform channel which will convey messages to the router,

(def router-transform-chan (chan 10))

;; then we start the router providing a name and the input channel.

(app-route/router [:router :x] router-transform-chan)

;; The name of this router is [:router :x] and could be any path.
;; By convention, the path usually contains `:router`, which helps
;; with readability.
;; To add transform messages to the router we send messages to it on the
;; input transform channel. In the example below, we send one transform
;; message with three transformations, each one adding a channel to
;; the router.

(put! router-transform-chan [[[:router :x] :add [ui-transform-chan [:ui :**] :*]]
                             [[:router :x] :add [info-transform-chan [:info :**] :*]]
                             [[:router :x] :add [service-transform-chan [:service :**] :*]]])

;; The argument/state part of the transformation is the same kind of vector
;; that we would use to configure a map except that we have a channel
;; in the place of a function.  The router's only function is to route to
;; other channels.  Let's take a closer look...

[ui-transform-chan [:ui :**] :*]

;; This means that any incoming transform message for any event where
;; the first element in the target is `:ui` will get put on the
;; `ui-transform-chan` channel.

;; We can now send a single transform message to this router and it
;; will split it into three transforms and place each one on the
;; correct outbound channel.

(put! router-transform-chan [[[:ui :counter-id :a] :set-value 42]
                             [[:info :counter :a] inc]
                             [[:info :counter :b] inc]
                             [[:service :datomic] :save-value :counter-a 42]])

(println (first (alts!! [ui-transform-chan (timeout 100)])))
(println (first (alts!! [info-transform-chan (timeout 100)])))
(println (first (alts!! [service-transform-chan (timeout 100)])))

;; to remove a channel, we send the router a `:remove` transform.
;; Let's remove the model transforms from the router:

(put! router-transform-chan [[[:router :x] :remove [info-transform-chan [:info :**] :*]]])

;; Now when we send the same message as above, nothing will be put on
;; the `info-transform-chan`.

(put! router-transform-chan [[[:ui :counter-id :a] :set-value 42]
                             [[:info :counter :a] inc]
                             [[:info :counter :b] inc]
                             [[:service :datomic] :save-value :counter-a 42]])

(println (first (alts!! [ui-transform-chan (timeout 100)])))
(println (first (alts!! [info-transform-chan (timeout 100)])))
(println (first (alts!! [service-transform-chan (timeout 100)])))

;; Let's expand on our earlier pipeline.
;; Suppose we had two counters and a third counter which is always the
;; sum of the other two.  Anytime any of the counters change (that is, anytime
;; our internal model changes) we want to update the UI and notify the server,
;; where our Datomic DB lives.

;; Create a function that turns info model informs into Datomic service transforms.

(defn counter-datomic-transform [inform-message]
  (vector
   (mapv (fn [[source _ _ new-model]]
           (let [counter-id (last source)]
             [[:service :datomic] :save-value counter-id (get-in new-model source)]))
         inform-message)))

;; Create an output config that is capable of updating the UI and the server
;; anytime a counter is updated.

(def output-config [[counter-text-transform [:info :counter :*] :updated]
                    [counter-datomic-transform [:info :counter :*] :updated]])

;; Create and name the router.

(def router-transform-chan (chan 10))
(app-route/router [:router :x] router-transform-chan)

;; Create the route'd transform channels and add them to the router

(def ui-transform-chan (chan 10))
(def datomic-transform-chan (chan 10))

(put! router-transform-chan [[[:router :x] :add [ui-transform-chan [:ui :**] :*]]
                             [[:router :x] :add [datomic-transform-chan [:service :datomic] :*]]])

;; Wire it all together, creating the top-level input channel

(def input-inform-chan
  (->> router-transform-chan
       (app-map/inform->transforms output-config)
       (app-flow/transform->inform {:info {:counter {:a 0 :b 0 :c 0}}} flow-config)
       (app-map/inform->transforms input-config)))

;; Click and read!

(put! input-inform-chan [[[:ui :button :a] :click]])
(put! input-inform-chan [[[:ui :button :b] :click]])

(println (first (alts!! [ui-transform-chan (timeout 100)])))
(println (first (alts!! [datomic-transform-chan (timeout 100)])))

;; Congrats, you're well on your way to building Cornice Apps!


