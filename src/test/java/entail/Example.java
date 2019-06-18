package entail;

import core.Entailment;

public class Example {
	
	private static String configFile = "C:\\XTE\\config.txt"; //the configuration file path
	private static String kb = "WN"; //WN (WordNet), WKT (Wikitionary), WKP (Wikipedia) or WBT (Webster's)
	private static Entailment entail = new Entailment(kb, configFile);
	
	//Train the TED module
	private static void train (String dataset){
		
		entail.train(dataset);
	}
	
	//Test a single T-H pair
	private static void testPair (String text, String hypothesis){
		
		String result = entail.processPair(text, hypothesis, kb);
		System.out.println(result);
	}
	
	//Test a dataset
	private static void testDataset(String dataset, String outputFile){
		
		entail.processDataset(dataset, outputFile, kb);
	}
	
	public static void main (String[] args){
		
		String trainDataset = "C:\\XTE\\Datasets\\RTE+SICK_train_set.txt"; //the training dataset (in text format)
		String testDataset = "C:\\XTE\\Datasets\\bpi-rte.txt"; //the test dataset (in text format)
		String outputFile ="C:\\XTE\\output.txt"; //the file where the results of testDataset will be written to
		
		String text = "A council worker cleans up after Tuesday's violence in Budapest."; //a sample text
		String hypothesis = "There was damage in Budapest."; //a sample hypothesis
		
		train(trainDataset);
		testPair(text, hypothesis);
		testDataset(testDataset, outputFile);
	}

}
