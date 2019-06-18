package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import at.unisalzburg.dbresearch.apted.costmodel.PerEditOperationStringNodeDataCostModel;
import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.Node;
import at.unisalzburg.dbresearch.apted.node.StringNodeData;
import at.unisalzburg.dbresearch.apted.parser.BracketStringInputParser;
import data.EntailmentDecision;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import util.Configuration;
import util.TreeFormatter;

public class TreeEditDistance {
	
	private final float delCost = 2;
	private final float insCost = 2;
	private final float repCost = 3;
	private float threshold;
	private StanfordCoreNLP pipeline;
	
	public TreeEditDistance(String configfile){
		
		//Disable log messages
		Logger.getRootLogger().setLevel(Level.OFF);
		
		//Read the learned threshold from the config file
		Configuration config = new Configuration(configfile);
		threshold = Float.parseFloat(config.params.get("tedthreshold"));
		
		//Initialize the dependency parser
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, depparse");
		RedwoodConfiguration.empty().capture(System.err).apply();
	    pipeline = new StanfordCoreNLP(props);
	    RedwoodConfiguration.current().clear().apply();
	}
	
	//Get the dependency parse of a piece of text and convert each sentence to a bracketed-style tree
	private List<String> makeTree (String text){
		
		List<String> trees = new ArrayList<String>();
		
		CoreDocument document = new CoreDocument(text);
		pipeline.annotate(document);
		
		for (CoreSentence sentence : document.sentences()){
			SemanticGraph dependencyParse = sentence.dependencyParse();
			TreeFormatter formatter = new TreeFormatter();
		
			String tree = formatter.convertToTree(dependencyParse);
			trees.add(tree);
		}	
		
		return trees;
	}
	
	//Normalize the distance, returning a value relative to the difference between the sizes of the trees
	private float normalizeDistance (float distance, int treeDiff){

		treeDiff = (treeDiff == 0) ? 1 : treeDiff; //Avoid division by zero

		return Math.round((distance / treeDiff) * 100);

	}
	
	//Compute the edit distance between two trees
	public float computeDistance (String text, String hyp){
		
		float minDist = Float.MAX_VALUE;
		
		BracketStringInputParser treeParser = new BracketStringInputParser();
		List<String> textTrees = makeTree(text);
		List<String> hypTrees = makeTree(hyp);
		
		PerEditOperationStringNodeDataCostModel costModel = new PerEditOperationStringNodeDataCostModel(delCost, insCost, repCost);
		APTED<PerEditOperationStringNodeDataCostModel, StringNodeData> apted = new APTED<>(costModel);
		
		//If the text and/or the hypothesis have more than one sentence and yields more than one tree,
		//compute the TED between each text tree and each hypothesis tree, and get the minimum distance
		for (String textTree : textTrees){
			for (String hypTree : hypTrees){
				
				Node<StringNodeData> tTree = treeParser.fromString(textTree);
				Node<StringNodeData> hTree = treeParser.fromString(hypTree);
		
				float distance = apted.computeEditDistance(tTree, hTree);
				int treeDiff = Math.abs(tTree.getNodeCount() - hTree.getNodeCount());
				
				distance = normalizeDistance(distance, treeDiff);
				
				if (distance <= minDist){
					minDist = distance;
				}
			}
		}
		
		return minDist;
	}

	
	//Compute the tree edit distance between the text and the hypothesis
	public EntailmentDecision computeEntailment(String text, String hyp) {
	   	
		EntailmentDecision answer = new EntailmentDecision();
		answer.setModel("EditDistance");
		
		BracketStringInputParser treeParser = new BracketStringInputParser();
		PerEditOperationStringNodeDataCostModel costModel = new PerEditOperationStringNodeDataCostModel(delCost, insCost, repCost);
		APTED<PerEditOperationStringNodeDataCostModel, StringNodeData> apted = new APTED<>(costModel);
		
		List<String> textTrees = makeTree(text);
		List<String> hypTrees = makeTree(hyp);
		float minDist = Float.MAX_VALUE;
		
		//If the text and/or the hypothesis have more than one sentence and yields more than one tree,
		//compute the TED between each text tree and each hypothesis tree, and get the minimum distance
		for (String textTree : textTrees){
			for (String hypTree : hypTrees){
				Node<StringNodeData> tTree = treeParser.fromString(textTree);
				Node<StringNodeData> hTree = treeParser.fromString(hypTree);
				
				float distance = apted.computeEditDistance(tTree, hTree);
				int treeDiff = Math.abs(tTree.getNodeCount() - hTree.getNodeCount());
				
				distance = normalizeDistance(distance, treeDiff);
				
				if (distance <= minDist){
					minDist = distance;
				}
			}
		}
		
		if (minDist <= threshold){
			answer.setDecision("yes");
			answer.setJustification("Hypothesis is a syntactic variation of the text.");
		}
		else{
			answer.setDecision("no");
			answer.setJustification("null");
		}
		
		return answer;
	}	

}
