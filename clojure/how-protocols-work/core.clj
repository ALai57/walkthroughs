(ns how-protocols-work
  "This walkthrough shows how Clojure's protocols work under the hood")






;; First, let's define a Protocol
(defprotocol FileSystem
  "Methods for interacting with a FileSystem"
  (ls [_ path] "List contents of a directory at `path`")
  (rm [_ path] "Remove file found at `path`")
  (cp [_ src dest] "Copy a file or directory from `src` to `dest`"))


;; Let's stop and inspect what happened as a result of calling `defprotocol`
;; There are two things in particular that we'd like to look at closely:
;;   (1) creation of a `var`, `FileSystem`
;;   (2) creation of functions for each protocol method [`ls` `rm` `cp`]
;;
;; There is also one thing that we can't inspect very closely
;;   (3) creation of a `FileSystem` class file with a `Filesystem` Java interface




;;
;; (1) Let's inspect the `FileSystem` var that was emitted
;;
(comment

  FileSystem

  ;; In our example, the var `FileSystem` is a hash-map with keys:
  ;; [`:on` `:on-interface` `:doc` `:var` `:method-map` `:sigs` `:method-builders`]
  )












;;
;; (2) Let's inspect the protocol methods that were emitted: `ls` `rm` `cp`
;;
(comment

  ls

  rm

  cp

  (every? fn? [ls rm cp])

  ;; Each of these protocol methods are just normal Clojure functions: `ls` `rm` `cp`
  )


















;; So how does this help us get type-based dispatch?
;; The Clojure compiler will help us out! If we have created any classes that
;;   implement the `FileSystem` interface, the compiler will dispatch the Java
;;   methods on that class. That's great, because it's fast!
;;
;; Also, each protocol method that is emitted (e.g. `ls` `rm` `cp`) has a lookup
;;   table (a cache) associated with it. Whenever you call the protocol method,
;;   and there is no underlying class file, our protocol method will inspect the
;;   cache to determine if it knows how to dispatch the method for the arguments
;;   supplied. For example when we first create the protocol, the `ls` method
;;   has a lookup table of known implementations. Since we haven't implemented
;;   the `FileSystem` protocol anywhere else in our codebase, we have no entries
;;   in the table.


(comment
  ;; Protocol methods use the `.__methodImplCache`  property to store the cache.
  ;; The property is part of the Abstract Class `AFunction` (which protocol
  ;;   methods implement).

  ;; Save the cache so we can inspect it
  (def ls-method-cache
    (.__methodImplCache ls))

  (type ls-method-cache)

  ;; Currently, the cache is empty. There are no known implementations of
  ;; `FileSystem`
  (seq (.table ls-method-cache))


  ;; Let's check and see if we know how to dispatch `ls` for a java.lang.Object
  (.fnFor ls-method-cache java.lang.Object)
  )
















;; Now let's extend the `FileSystem` protocol to a Clojure atom
;;   NOTE: We will only implement the `ls` method, for now.

(comment

  ;; We don't actually _own_ the clojure Atom data type. The maintainers of
  ;; Clojure do. We don't want to rewrite it to add the `ls` method we care
  ;; about. So, what can we do to extend its functionality without overriding
  ;; the base type? The answer is we can extend protocols to datatypes
  ;;
  ;; By extending `FileSystem` to `clojure.lang.Atom`, we are teaching Atoms how
  ;; to perform methods of the `FileSystem` protocol. But the underlying Atom
  ;; class does not actually implement these methods. So when we try to dispatch
  ;; `ls`, we can't look for the `ls` method on the `clojure.lang.Atom` class.
  ;;
  ;; This is where the `MethodImplCache` comes into play. This is a clojure
  ;; solution to extending methods to datatypes that you don't own. Once we use
  ;; the `ls` method, we will add it to our cache of known ways to dispatch `ls`
  (extend-type clojure.lang.Atom
    FileSystem
    (ls [this path]
      (get-in @this (clojure.string/split path #"/"))))

  ;; Before we actually call the `ls` method on the atom, we should still have
  ;; nothing in our cache
  (seq (.table (.__methodImplCache ls)))


  ;; Call the method on the atom, thereby adding the method to the table
  (ls (atom {"foo" {"bar" [:Hello]}})
      "foo/bar")

  (def ls-method-cache-after-usage
    (.__methodImplCache ls))

  ;; The cache should now have a method for `clojure.lang.Atom` in its `table`
  ;; of known methods
  (partition 2 (seq (.table ls-method-cache-after-usage)))

  ;; Let's lookup the `ls` function for an atom
  (.fnFor ls-method-cache-after-usage
          clojure.lang.Atom)


  )






















;; Now let's create an S3 defrecord. The defrecord macro emits a Java Class that
;; implements the `FileSystem` interface - this means that we can skip the
;; Method cache and directly invoke the interface instead (which is faster!)
;;
;; When we try to call the protocol method, Clojure will directly dispatch to
;; the underlying class methods on the emitted class. As a result, we don't
;; update our `MethodImplCache`
(defrecord S3 []
  FileSystem
  (ls [_ path]
    {:baz :qux}))

(comment

  ;; Call the method to check that it works
  (ls (S3.) nil)

  ;; Save the cache to inspect it
  (def ls-method-cache-after-calling-s3
    (.__methodImplCache ls))

  ;; When we called the `ls` method on the defrecord, we actually skipped the
  ;; cache entirely! Instead, invocation was delegated to the underlying
  ;; class. That means that we don't modify our `MethodImplCache`
  (partition 2 (seq (.table ls-method-cache-after-calling-s3)))

  ;; Let's directly check the cache and verify that we don't have an `ls` method
  ;; for an S3 record
  (.fnFor ls-method-cache-after-calling-s3
          (clojure.lang.Util/classOf (S3.)))

  )
