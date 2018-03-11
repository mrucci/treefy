#!/usr/bin/env python

"""
Transform a list of file paths into an interactively explorable tree.
"""

from collections import defaultdict
import json


def Tree():
    """
    A mutable tree implementation where the nodes are reprensented by the keys of nested dictionaries.
    """
    return defaultdict(Tree)


def print_tree(tree, l=0):
    for k, v in tree.iteritems():
        l += 1
        print "".join(['-']*l*2), str(k)
        print_tree(v, l)
        l -= 1


def access(tree, seq):
    """Return tree[seq[0][seq[1]][...] with the side-effect of creating all nodes in the path."""
    if len(seq) == 0:
        return None
    if len(seq) == 1:
        return tree[seq[0]]
    return access(tree[seq[0]], seq[1:])


def treefy(seqs):
    """
    input:
    [
      (code, app, .git),
      (code, app, .git, 1),
      (code, app, source, x),
      (code, web, some, .git, ...),
    ]
    """
    t = Tree()
    _ = [access(t, seq) for seq in seqs]
    return t


def first(tree):
    return tree.items()[0]


def first_key(tree):
    return first(tree)[0]


def first_value(tree):
    return first(tree)[1]


def is_single_leaf(tree):
    """Return true if the tree is actually just a single leaf node."""
    return len(tree) == 1 and len(tree[tree.keys()[0]]) == 0


def is_leaf(node):
    """A node is a leaf if it has zero children (its subtree is empty)."""
    return len(node[1]) == 0


assert treefy([]) == {}
assert treefy([(1, )]) == {1: {}}
assert treefy([(1, 2, )]) == {1: {2: {}}}
assert treefy([(1, 2, ), (3, )]) == {1: {2: {}}, 3: {}}


def simplify(tree):
    """
    Remove the longest chain of single-child nodes from the root (preserving the leafs).
    This operation can be used for example to remove the common path from a tree representing a set of file paths.
    """
    def go(tree, simplified):
        if len(tree) == 1 and not is_leaf(first(tree)):
            k = tree.keys()[0]
            return go(tree[k], simplified + [k])
        else:
            return tree, simplified

    return go(tree, [])


assert simplify(treefy([])) == ({}, [])
assert simplify(treefy([(1, )])) == ({1: {}}, [])
assert simplify(treefy([(1, 2, )])) == ({2: {}}, [1])
assert simplify(treefy([(1, 2, 3, ), (1, 2, 4, )])) == ({3: {}, 4: {}}, [1, 2])


def join_shared_paths(tree, join_fn):
    """Recursively join common paths in the tree"""
    if len(tree) == 0 or is_single_leaf(tree):
        return tree

    new_tree, common_path = simplify(tree)
    ## join root values with common_path
    for k in new_tree.keys():
        new_tree[join_fn(common_path + [k])] = new_tree.pop(k)

    ## TODO: recurse!
    return new_tree


class FoldableNode(object):

    def __init__(self, value, status=0, index=-1):
        """
        A foldable node.

        status = (0:closed, 1:open).
        """
        self.value = value
        self.status = status
        self.index = index

    def __str__(self):
        return self.value

    def __repr__(self):
        return "FoldableNode(%r,%r,%r)" % (self.value, self.status, self.index)

    def __eq__(self, other):
      return other and self.value == other.value

    def __hash__(self):
        return hash(self.value)

    def expand(self):
        self.status = 1

    def collapse(self):
        self.status = 0


def reindex(ftree, index=1):
    """Do a depth first pass and assign indices based on the visit order.  Stop traversing when the staus is closed."""
    for k, v in ftree.iteritems():
        ## Do not attach an index to leaf nodes since they cannot be expanded (index will be equal to -1).
        if len(v) == 0:
            continue
        k.index = index
        index += 1
        if k.status != 0:
            index = reindex(v, index)
    return index


def print_ftree(ftree, l=0):
    """Print unfolded branches."""
    for k, v in ftree.iteritems():
        l += 1
        indent = "".join([' ']*l*2)
        ## Treat leaf nodes differently
        if k.index != -1:
            print "%3d." % k.index, indent, "%s %s" % ("+" if k.status == 0 else "-", k.value)
        else:
            print "    ", indent, "* %s" % (k.value)

        if k.status != 0:
            print_ftree(v, l)
        l -= 1


def toggle_status(ftree, index):
    """Find the node with the specified index and toggle its status."""
    for k, v in ftree.iteritems():
        if k.index == index:
            k.status = 1 - k.status
            break
        if k.status == 1:
            toggle_status(v, index)


def depth_first(ftree, node_fn):
    for k, v in ftree.iteritems():
        node_fn(k, v)
        depth_first(v, node_fn)


def expand_all(ftree):
    depth_first(ftree, lambda n, _: n.expand())


def collapse_all(ftree):
    depth_first(ftree, lambda n, _: n.collapse())


def expand_single_child_nodes(ftree):
    if len(ftree.keys()) == 1:
        ftree.keys()[0].status = 1
    for k, v in ftree.iteritems():
        expand_single_child_nodes(v)


if __name__ == "__main__":
    import sys

    lines = sys.stdin.readlines()
    seqs = [line.strip().split('/') for line in lines]

    if True:  ## TODO: this logic should only be applied if the input represents file paths...
        ## Add back the slashes to identify folders (that is, non leaf nodes)
        seqs = [[p + '/' for p in seq[:-1]] + [seq[-1]] for seq in seqs]
        ## Remove empty '' entries (as a result of "bla/".split())
        seqs = [[p for p in seq if len(p) > 0] for seq in seqs]

    print_tree(treefy(seqs))
    seqs = [map(FoldableNode, seq) for seq in seqs]
    t = treefy(seqs)
    if True:  ## TODO: this should be a CLI option or maybe just a command.
        t = join_shared_paths(t, lambda seq: FoldableNode("".join(s.value for s in seq)))
    if True:  ## TODO: this should be a CLI option or maybe just a command.
        expand_single_child_nodes(t)

    print_tree(t)

    ## Reopen stdin for reading user input
    sys.stdin.close()
    sys.stdin = open('/dev/tty', 'r')

    print ""
    while True:
        reindex(t)
        print_ftree(t)
        user_choice = raw_input("Select node to toggle: ")
        if user_choice == 'A':
            expand_all(t)
        else:
            ## TODO: validate user_choice (check bounds)
            toggle_status(t, int(user_choice))

