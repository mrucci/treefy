# treefy


`treefy` is a little Clojure command line tool that takes as input a list of file paths, it transforms them into a tree data structure and displays it, allowing interactive exploration of the tree by collapsing and expanding selected folders.

As an example, `treefy` can be used to better understand the output of a dry-run rsync execution that involves many files and directories.

Here it is in action:

[![asciicast](https://asciinema.org/a/Mq3m9EfvsC8SQaJdvpp4lsIFt.png)](https://asciinema.org/a/Mq3m9EfvsC8SQaJdvpp4lsIFt)


## Usage examples

    ## Browse the file system as a tree
    find . -type f | lein run

    ## Interpret the output of rsync
    rsync -av --dry-run src dst | grep 'src/' | lein run


## History and motivation

`treefy` was initially written in Python using a mutable tree data structure based on defaultdict.
The need/excuse for rewriting it in Clojure arose while implementing one of the tree manipulation functions that ended up particularly difficult to implement due to tree being modified while traversed, producing unpredictable results and tricky reasoning.
I then began exploring an alternative implementation using the Clojure immutable hash maps to represent the tree and functional zippers for navigation and editing.

The result was surprisingly positive.  The immutable data structure of Clojure, combined with the intuitive navigation and editing via zippers, allowed a quicker and bug-free implementation of the same functions.


