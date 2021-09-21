# treefy

`treefy` is a command line tool written in Clojure that takes a list of file paths and displays it as a tree, allowing interactive exploration by collapsing and expanding selected paths.

For example, `treefy` can be used to easily interpret a dry-run `rsync` output involving many files and directories.

Here it is in action:

[![asciicast](https://asciinema.org/a/437064.svg)](https://asciinema.org/a/437064?autoplay=1)


## Usage examples

    ## Browse the file system as a tree
    find . -type f | lein run

    ## Interpret the output of rsync
    rsync -av --dry-run src dst | grep 'src/' | lein run

Instead of `lein run`, the [babashka](https://babashka.org/) interpreter can also be used:

    alias treefy='bb -f /path/to/treefy/src/treefy/core.clj'


## History and motivation

`treefy` was initially written in Python using a mutable tree data structure based on defaultdict.
The need/excuse for rewriting it in Clojure arose while implementing one of the tree manipulation functions that ended up particularly difficult to implement due to tree being modified while traversed, producing unpredictable results and tricky reasoning.
I then began exploring an alternative implementation using the Clojure immutable hash maps to represent the tree and functional zippers for navigation and editing.

The result was surprisingly positive.  The immutable data structure of Clojure, combined with the intuitive navigation and editing via zippers, allowed a quicker and bug-free implementation of the same functions.


