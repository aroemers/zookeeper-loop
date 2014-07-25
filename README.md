# zookeeper-loop

A Clojure library designed to have an automatically reconnecting Zookeeper client, for use with for example [zookeeper-clj](#).

## Usage

Create a client loop like so:

```clojure
(require '[zookeeper-loop :refer (client-loop close-loop)])

(def client (client-loop "localhost:2181"))
```

One can pass options to the client-loop as one would to `zookeeper/connect` of [zookeeper-clj](#). One extra option is `:client-atom`. This will then be used to hold the currently used `Zookeeper` instance. Use at your own risk.

Now that you have a client-loop, one can use it as follows:

```clojure
;; from zookeeper-clj
(require '[zookeeper :as zk])

;; Just deref the client-loop, and a connected instance will be returned.
(zk/create-all @client "/foo/bar")

;; A client may be in a 'connecting' state forever, so a more sensible approach
;; cound be to use timeouts.
(zk/data (deref client 2000 ::fail) "/foo/bar")
```

A connection-related exception might still be thrown in above statement, for instance when the client is closed by you, authentication failed, or the connection changed between the deref and the actual execution of the expression. Dealing with these cases is the responsibility of the user. One can of course easily wrap these statements with more sophisticated retry logic.

To close the client, and stop the loop:

```clojure
(close-loop client)
```


## Patterns

The `zookeeper-loop.patterns` namespace contains some frequently used Zookeeper patterns. These patterns use a client-loop, so they are implemented in a more robust and failure-friendly way. Currently the following patterns are available:

### ensure-path

Ensures the given path exists. When the connection to Zookeeper cannot be (re)established within timeout-msec while processing the path, an exception is thrown. Returns the path when created, or nil when it already exists

### swap!

Applies the given function atomically (a compare-and-set loop) to the data of the given path, together with the args. When the connection to Zookeeper cannot be (re)established within timeout-msec while processing the swap, an exception is thrown. Requires `*deserializer*` and `*serializer*` to be bound to functions taking a byte-array or a value respectively. Returns the new value.


## TODO

* Have more influence on how to deal with expired clients when `deref`ing?
