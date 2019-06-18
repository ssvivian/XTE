package util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.MapFactory;

public class TreeFormatter {
	
	private static final MapFactory<IndexedWord, IndexedWord> wordMapFactory = MapFactory.hashMapFactory();
	
	//Recursively get all children of a graph's node and append them to the tree
	private void getAllChildren(SemanticGraph graph, IndexedWord currentNode, StringBuilder tree, Set<IndexedWord> visited) {
		
		visited.add(currentNode);
	    List<SemanticGraphEdge> edges = graph.outgoingEdgeList(currentNode);
	    Collections.sort(edges);
	    
	    if (edges.isEmpty()){
	    	tree.append("}}");
	    }
	    else{
		    for (SemanticGraphEdge edge : edges) {
		    	IndexedWord target = edge.getTarget();
		    	tree.append("{").append(edge.getRelation()).append("{").append(target.lemma().toLowerCase());
		    	
		    	if (!visited.contains(target)) { //recurse
		    		getAllChildren(graph, target, tree, visited);
		    	}
		    	else{
		    		tree.append("}}");
		    	}
		    }
		    tree.append("}}");
	    } 
	}

	//Convert a dependency parse graph to a bracketed-style tree
	public String convertToTree (SemanticGraph dependencyParse){
		
		StringBuilder tree = new StringBuilder();
		
		Collection<IndexedWord> rootNodes = dependencyParse.getRoots();
		Set<IndexedWord> visited = wordMapFactory.newSet();
		
		for (IndexedWord root : rootNodes) {
			tree.append("{").append(root.lemma().toLowerCase());
			getAllChildren(dependencyParse, root, tree, visited);
			tree.deleteCharAt(tree.length()-1); //Last bracket is not necessary
		}
		
		Set<IndexedWord> nodes = wordMapFactory.newSet();
	    nodes.addAll(dependencyParse.vertexSet());
	    nodes.removeAll(visited);
	    
	    while (!nodes.isEmpty()) {
	    	IndexedWord node = nodes.iterator().next();
	    	tree.append(node.lemma().toLowerCase());
	    	getAllChildren(dependencyParse, node, tree, visited);
	    	nodes.removeAll(visited);
	    }
	    
	    //Check whether the final tree is syntactically consistent
	    int lBrackets = StringUtils.countMatches(tree.toString(), "{");
	    int rBrackets = StringUtils.countMatches(tree.toString(), "}");
	    
	    if (lBrackets != rBrackets){
	    	System.out.println("Error: Number of left and right brackets don't match.");
	    	System.exit(1);
	    }
		
		return tree.toString();
	}

}
