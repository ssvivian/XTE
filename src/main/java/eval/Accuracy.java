package eval;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

public class Accuracy {
	
	//Model precision
	public Double computePrecision (Double tp, Double fp){
		
		return tp / (tp + fp);
	}
	
	//Model recall
	public Double computeRecall (Double tp, Double fn){
		
		return tp / (tp + fn);
	}
	
	//Model F1 score
	public Double computeF1 (Double precision, Double recall){
		
		return (2 * precision * recall) / (precision + recall);
	}
	
	//Print the results for a single model
	private void printPartialResults(List<JSONObject> results){
		
		Double tp = 0.0;
		Double fp = 0.0;
		Double fn = 0.0;
		Double tn = 0.0;
		
		for (JSONObject result : results){
			String entail = (String) result.get("entailment");
			String answer = (String) result.get("answer");
			
			if (entail.equalsIgnoreCase("yes")){
				if (answer.equalsIgnoreCase("yes")){
					tp += 1.0;
				}
				else{
					fn += 1.0;
				}
			}
			else{
				if (answer.equalsIgnoreCase("yes")){
					fp += 1.0;
				}
				else{
					tn += 1.0;
				}
			}
		}
		
		Double precision = computePrecision(tp, fp);
		Double recall = computeRecall(tp, fn);
		
		System.out.println("Summary");
		System.out.println("-------");
		System.out.println("True positives: " + tp);
		System.out.println("False positives: " + fp);
		System.out.println("True negatives: " + tn);
		System.out.println("False negatives: " + fn);
		System.out.println();
		System.out.println("Precision: " + String.format("%.2f", precision));
		System.out.println("Recall: " + String.format("%.2f", recall));
		System.out.println("F-measure: " + String.format("%.2f", computeF1(precision, recall)));
		
	}
	
	//Print a summary with all the results for each model
	public void printSummary (List<JSONObject> results){
		
		List<JSONObject> tedResults = new ArrayList<JSONObject>();
		List<JSONObject> gnResults = new ArrayList<JSONObject>();
		
		for (JSONObject result : results){
			String model = (String) result.get("model");
			
			if (model.equals("TreeEditDistance")){
				tedResults.add(result);
			}
			else if (model.equals("GraphNavigation")){
				gnResults.add(result);
			}
		}
		
		printPartialResults(results);
		System.out.println("\n***** Model: Tree Edit Distance *****\n");
		printPartialResults(tedResults);
		System.out.println("\n***** Model: Graph Navigation *****\n");
		printPartialResults(gnResults);
	}

}
