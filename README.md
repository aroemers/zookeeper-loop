# zookeeper-loop

A Clojure library designed to have an automatically reconnecting Zookeeper client, for use with for example [zookeeper-clj](https://github.com/liebke/zookeeper-clj).

## Usage

Add the following dependency to your project:

```clojure
[functionalbytes/zookeeper-loop "0.1.0"]
```

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

A connection-related exception might still be thrown in above statement, for instance when the client is closed by you, authentication failed, or the connection changed between the deref and the actual execution of the expression. Dealing with these cases is the responsibility of the user. One can of course easily wrap these statements with more sophisticated retry logic (e.g. the patterns below).

To close the client, and stop the loop:

```clojure
(close-loop client)
```


## Patterns

The `zookeeper-loop.patterns` namespace contains some frequently used Zookeeper patterns. These patterns use a client-loop, so they are failure-friendly. Currently the following patterns are available:

### retry

Tries to execute the given function on a connected Zookeeper client as the first argument. When the connection to Zookeeper cannot be (re)established within timeout (msec) while processing the function, an exception is thrown. Note that in rare cases the `retry` pattern may take longer than the given timeout, when a newly established connection gets disconnected right away, i.e. just before the given function can be executed. Returns the value of the evaluated function. For example:

```clojure
(retry client 1000 zk/delete "/path/to/delete")
```

### ensure-path

Ensures the given path exists, based on the `retry` pattern above. Returns the path when created, or nil when it already exists. For example:

```clojure
(ensure-path client 1000 "/path/to/check/or/create")
```

### swap!

Applies the given function atomically (a compare-and-set loop) to the data of the given path, together with the args. This is based on the `retry` pattern above. Requires `*deserializer*` and `*serializer*` to be bound to functions taking a byte-array and a value respectively. Returns the new value. For example:

```clojure
;; Atomically add an exclamation to a String.
(binding [*deserializer* #(String. % "UTF-8")
          *serializer* #(.getBytes % "UTF-8")]
  (swap! client 1000 "/path/to/a/string/node" str "!"))
```


## TODO

* Have more influence on how to deal with expired clients when `deref`ing?
* Have *deserializer* and *serializer* default to `identity`?
