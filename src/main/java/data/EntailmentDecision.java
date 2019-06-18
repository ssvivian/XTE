package data;

public class EntailmentDecision {
	
	private String model;
	private String decision;
	private String justification;
	
	public void setModel (String m){
		
		model = m;
	}
	
	public void setDecision (String d){
		
		decision = d;
	}

	public void setJustification (String j){
	
		justification = j;
	}
	
	public String getModel (){
		
		return model;
	}
	
	public String getDecision (){
		
		return decision;
	}

	public String getJustification (){
	
		return justification;
	}

}
