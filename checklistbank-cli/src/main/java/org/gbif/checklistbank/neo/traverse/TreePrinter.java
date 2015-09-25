package org.gbif.checklistbank.neo.traverse;

import org.gbif.api.vocabulary.Rank;
import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NodeProperties;

import java.io.PrintStream;

import org.neo4j.graphdb.Node;
import org.parboiled.common.StringUtils;

/**
 * A handler that can be used with the TaxonWalker to print a neo4j taxonomy in a simple nested text structure.
 */
public class TreePrinter implements StartEndHandler {
    final int indentation;
    final private PrintStream out;
    int level = 0;

    public TreePrinter(int indentation, PrintStream out) {
        this.indentation = indentation;
        this.out = out;
    }

    public TreePrinter(int indentation) {
        this(indentation, System.out);
    }

    public TreePrinter(PrintStream out) {
        this(2, out);
    }

    public TreePrinter() {
        this(2, System.out);
    }

    @Override
    public void start(Node n) {
        print(n);
        level++;
        for (Node s : Traversals.SYNONYMS.traverse(n).nodes()) {
            print(s);
        }
    }

    @Override
    public void end(Node n) {
        level--;
    }

    private void print(Node n) {
        out.print(StringUtils.repeat(' ', level * indentation));
        if (n.hasLabel(Labels.SYNONYM)) {
            out.print("*");
        }
        out.print(n.getProperty(NodeProperties.SCIENTIFIC_NAME));
        if (n.hasProperty(NodeProperties.RANK)) {
            out.println(" [" + Rank.values()[(Integer)n.getProperty(NodeProperties.RANK)] + "]");
        }
    }
}
