package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;
import java.util.function.Predicate;

import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import data.EntailmentDecision;
import graph.DefinitionGraph;
import util.Configuration;
import util.IndraCall;
import util.SynsetTable;
import util.TextHandler;

public class GraphNavigation {
	
	private static final int maxEntries = 5;
	private static final int maxDepth = 5;
	private static final int maxPaths = 100;
	private static final int searchLimit = 200;
	private static SynsetTable synTable = new SynsetTable();
	private static DefinitionGraph dg;
	private static TextHandler th;
	
	public GraphNavigation (TextHandler handler, String kb, String configfile){
		
		//Disable log messages
		Logger.getRootLogger().setLevel(Level.OFF);
		
		//Initialize the text handler and set the distributional model
		th = handler;
		
		//Load the RDF KBs and initialize the IDF calculator
		System.out.print("Loading knowledge base... ");
		Configuration config = new Configuration(configfile);
		
		if (kb.equals("WN")){
			dg = new DefinitionGraph(config.params.get("wngraph"));
		}
		else if (kb.equals("WKT")){
			dg = new DefinitionGraph(config.params.get("wktgraph"));
		}
		else if (kb.equals("WKP")){
			dg = new DefinitionGraph(config.params.get("wkpgraph"));
		}
		else if (kb.equals("WBT")){
			dg = new DefinitionGraph(config.params.get("wbtgraph"));
		}
		System.out.println("Done.\n");
	}
	
	//Check whether two words are synonyms
	private boolean areSynonyms (String word1, String word2, String pos){
		
		boolean syns = false;
		
		List<String> synonyms = synTable.getSynonyms(word1, pos);
		if (synonyms.contains(word2)){
			syns = true;
		}
		
		return syns;
	}
	
	//Find the best threshold for a ranked list of scores using semantic differential analysis
	@SuppressWarnings("rawtypes")
	private Double computeThreshold (List<Map> scores){
		
		Double maxDiff = 0.0;
		Double bottomValue = 0.0;
		
		for (int i=0; i < scores.size()-1; i++){			
			Double currScore = Math.abs((Double) scores.get(i).get("score"));
			Double nextScore = Math.abs((Double) scores.get(i+1).get("score"));
			Double diff = currScore - nextScore;
			
			if (diff > maxDiff){
				maxDiff = diff;
				bottomValue = nextScore;
			}
		}
		
		return bottomValue;
	}
	
	//Get the similarity scores for all the pairs of terms and returns the terms with the highest similarity values
	@SuppressWarnings("rawtypes")
	private List<String> getBestMatches (String target, List<String> nodes, boolean ascending){

		List<String> bestMatches = new ArrayList<String>();
		List<Map> scores = IndraCall.getResponse(target, nodes);

		//Sort results, using insertion sort
		Map temp;

		for (int i=1; i < scores.size(); i++){			
			for (int j = i ; j > 0 ; j--){
				Map pair1 = scores.get(j);
				Map pair2 = scores.get(j-1);
				Double score1 = Math.abs((Double) pair1.get("score"));
				Double score2 = Math.abs((Double) pair2.get("score"));

				if (score1 < score2){
					temp = scores.get(j);
					scores.set(j, scores.get(j-1));
					scores.set(j-1, temp);
				}
			}
		}
		
		//Get all the terms whose similarity score is higher than the threshold
		Double threshold = computeThreshold(scores);
		for (Map pair : scores){			
			String match = (String) pair.get("t2");
			Double score = Math.abs((Double) pair.get("score"));

			if (score >= threshold){
				bestMatches.add(match);
			}
		}

		//If no value is higher than the threshold, get only the highest one
		if (bestMatches.size() == 0 && scores.size() > 0){
			bestMatches.add((String) scores.get(scores.size()-1).get("t2"));
		}
		else{
			//Reverse the results if descending order is required
			if (!ascending){
				List<String> reversed = new ArrayList<String>();

				for (int k=bestMatches.size()-1; k >= 0; k--){
					reversed.add(bestMatches.get(k));
				}
				bestMatches = reversed;
			}
		}

		return bestMatches;
	}
	
	//Get the main words in a segment according to their semantic similarity to the target word
	@SuppressWarnings("rawtypes")
	private List<String> getHeadWords (List<String> segment, String target, boolean ascending){
			
		List<String> headWords = new ArrayList<String>();
		
		//Remove stop words
  		Predicate<String> isStopWord = s -> th.isStopWord(s.split(";")[0]);
  		segment.removeIf(isStopWord);
		
		//Remove words with low IDF
  		segment = th.removeLowIDF(segment);
		
		if (segment.size() > 0){
			//Compute the semantic similarity between each word and the target
			List<Map> scores = IndraCall.getResponse(target, segment);
			
			//Sort words according to the similarity score
			Map temp;
			
			for (int i=1; i < scores.size(); i++){			
				for (int j = i ; j > 0 ; j--){
					Map pair1 = scores.get(j);
					Map pair2 = scores.get(j-1);
					Double score1 = Math.abs((Double) pair1.get("score"));
					Double score2 = Math.abs((Double) pair2.get("score"));
	
					if (score1 > score2){ //descending order
						temp = scores.get(j);
						scores.set(j, scores.get(j-1));
						scores.set(j-1, temp);
					}
				}
			}	
			
			//Get the <max> words with the highest similarity scores
			int maxWords = Math.min(scores.size(), maxEntries);
			if (ascending){
				for (int k=maxWords-1; k >= 0; k--){
					headWords.add((String) scores.get(k).get("t2"));			
				}
			}
			else{
				for (int k=0; k < maxWords; k++){
					headWords.add((String) scores.get(k).get("t2"));			
				}
			}
		}
		
  		return headWords;	
	}

	//Select the supertypes that are linked to a specific set of roles
	private List<String> filterSupertypes(Map<String, List<String>> tuples, List<String> segments){

		List<String> bestSupertypes = new ArrayList<String>();

		for (String segment : segments){
			for (String supertype : tuples.keySet()){
				if (tuples.get(supertype).contains(segment)){
					bestSupertypes.add(supertype);
				}
			}
		}

		return bestSupertypes;
	}

	//Calculate the number of nodes visited in the path
	private int getPathDepth (Vector<String> path){

		int depth = 0;

		if (path.size() > 50){ //path probably in loop, assign max depth + 1 to stop search
			depth = maxDepth + 1;
		}
		else{
			String nextNodeRole = new String();
			String previousNodeRole = "source";

			for (int i=0; i < path.size(); i++){
				String nodeRole = path.get(i).split(";")[1];

				if ((i+1) < path.size()){
					nextNodeRole = path.get(i+1).split(";")[1];
				}

				if (!nodeRole.equals("source") && !nodeRole.equals("supertype head") &&	!nodeRole.startsWith("head") 
						&& !nodeRole.startsWith("synonym") && (!nodeRole.startsWith("supertype") 
								|| (nodeRole.startsWith("supertype") && nextNodeRole.startsWith("supertype") 
										&& !nextNodeRole.startsWith("head") && !nextNodeRole.equals("supertype head")
										|| ((nextNodeRole.startsWith("head") || nextNodeRole.equals("supertype head")) 
												&& previousNodeRole.equals("source"))))){
					depth++;
				}
				previousNodeRole = nodeRole;
			}
		}	

		return depth;
	}

	//Take all the information that is no longer necessary off the path
	private Vector<String> cleanPath(Vector<String> path){

		Vector<String> newPath = new Vector<String>();

		newPath.add(path.get(0)); //source
		newPath.add(path.get(1)); //source's supertype

		//Remove circular references and consecutive duplicated steps in the path
		for (int i=2; i < path.size()-1; i++){
			String step = path.get(i);
			String stepNode = step.split("#")[0];
			String role = step.split(";")[1];

			String previousStep = path.get(i-1);
			String nextStep = path.get(i+1);
			String previousNewStep = newPath.get(newPath.size()-1);

			if (!step.equals(previousStep) && !step.equals(previousNewStep)
					&& (!role.equals("supertype of " + stepNode) 
							|| (role.equals("supertype of " + stepNode) && !nextStep.contains(";supertype")))
					&& !previousStep.equals(nextStep)){
				newPath.add(step);
			}
		}

		newPath.add(path.get(path.size()-1)); //target

		return newPath;
	}

	//Get the shortest path, i.e., the path with the shortest number of nodes visited
	@SuppressWarnings("unchecked")
	private Vector<String> getShortestPath (Vector<Vector<String>> paths){

		Vector<String> shortestPath = paths.get(0);
		int shortestDepth = getPathDepth(paths.get(0));

		for (Vector<String> path : paths){
			int depth = getPathDepth(path);

			if (depth < shortestDepth){
				shortestPath = (Vector<String>) path.clone();
				shortestDepth = depth;
			}
		}

		return shortestPath;
	}

	//Find the paths in the RDF graph between the source and target terms, DFS style
	@SuppressWarnings("unchecked")
	private Vector<Vector<String>> findPaths(String source, String sPOS, String target, String tPOS){

		Vector<Vector<String>> paths = new Vector<Vector<String>>();	
		Stack<Vector<String>> subpaths = new Stack<Vector<String>>();
		String Vnsp = dg.getVerbNamespace();
		String Nnsp = dg.getNounNamespace();
		int totalPathsTried = 0;
		boolean targetReached = false;

		String nsp = sPOS.startsWith("NN") ? Nnsp : Vnsp;
		String pos = sPOS.startsWith("VB") ? "VB" : "NN";
		tPOS = tPOS.startsWith("VB") ? "VB" : "NN";
		Vector<String> newPath = new Vector<String>();
		newPath.add(source + "#" + pos + ";source");
		subpaths.push(newPath);

		while (!subpaths.isEmpty()){

			Vector<String> currentPath = subpaths.pop();
			int depth = getPathDepth(currentPath);
			boolean match = false;
			String currentRole = currentPath.get(currentPath.size()-1).split(";")[1];
			String pathEnd = currentPath.get(currentPath.size()-1).split(";")[0];
			String lastNode = pathEnd.contains("#") ? pathEnd.split("#")[0] : pathEnd;
			pos = pathEnd.contains("#") ? pathEnd.split("#")[1] : pos;
			String nextNode = th.normalize(lastNode, pos);
			String lastSynsetNotFound = new String();
			
			if (nextNode.equals(th.normalize(target, tPOS))){
				match = true;
			}
			
			if (areSynonyms(nextNode, th.normalize(target, tPOS), pos)){
				currentPath.add(nextNode + "#" + pos + ";synonym of " + th.normalize(target, tPOS).replaceAll("_", " "));
			}

			while (!nextNode.equals(th.normalize(target, tPOS)) && depth <= maxDepth){
				match = false;
				currentRole = currentPath.get(currentPath.size()-1).split(";")[1];

				if (currentRole.contains("synonym")){
					match = true;						
					break;
				}

				nsp = pos.startsWith("NN") ? Nnsp : Vnsp;

				//Get all the synsets that contains the word/phrase currently being analyzed (starting by the source)
				List<Resource> synsets = dg.getSynsets(nextNode, nsp);

				if (synsets.size() > 0){
					
					//If the next node is still the source node, check all synonyms before going ahead
					if (currentRole.equals("source")){
						for (Resource synset : synsets){
							List<String> synWords = dg.getSynonyms(synset);

							for (String synWord : synWords){
								
								if (areSynonyms(synWord, th.normalize(target, tPOS).replaceAll("_", " "), pos)){
									currentPath.add(lastNode + "#" + pos + ";synonym of " + th.normalize(target, tPOS).replaceAll("_", " "));
									subpaths.push((Vector<String>) currentPath.clone());
									match = true;											
									break;
								}
							}
							
							if (match)
								break;
						}
					}
					
					if (!match){
						//Get all the supertypes of the retrieved synsets
						List<Resource> supertypes = dg.getSupertypes(synsets);
	
						//Get all the roles linked to the supertypes, then get the most similar ones w.r.t. the target
						Map<String, List<String>> tuples = dg.listRolesBySupertype(synsets, supertypes);
						List<String> allSegments = new ArrayList<String>();
	
						for (List<String> roleSet : tuples.values()){
							allSegments.addAll(roleSet);
						}
	
						List<String> cleanSegs = new ArrayList<String>();
						for (String seg : allSegments){ //strip off the role name
							cleanSegs.add(seg.split(";")[0]);
						}
	
						List<String> bestSegments = getBestMatches(th.normalize(target, tPOS).replaceAll("_", " "), cleanSegs, false);
	
						for (int l=0; l < bestSegments.size(); l++){ //put the role name back
							for (String segment : allSegments){
								if (segment.startsWith(bestSegments.get(l) + ";")){
									bestSegments.set(l, segment);
									break;
								}
							}
						}
	
						//Filter the supertypes on the best roles
						List<String> bestSupertypes = filterSupertypes(tuples, bestSegments);
	
						//Work on the first supertype and put all the other ones in the stack to be processed later
						Vector<String> currentPathBkp = (Vector<String>) currentPath.clone();
	
						for (int i=1; i < bestSupertypes.size(); i++){
							String bestSupertype = bestSupertypes.get(i);
							Vector<String> altSptPath = (Vector<String>) currentPathBkp.clone();
							pathEnd = altSptPath.get(altSptPath.size()-1).split(";")[0];
							lastNode = pathEnd.contains("#") ? pathEnd.split("#")[0] : pathEnd;
	
							//Get the synsets linked to this supertype
							List<Resource> bestSynsets = dg.getSynsetsBySupertype(synsets, bestSupertype);
	
							//Get all the synonyms to check whether one of them matches the target
							for (Resource synset : bestSynsets){
								List<String> synWords = dg.getSynonyms(synset);
	
								for (String synWord : synWords){
									if (areSynonyms(synWord, th.normalize(target, tPOS).replaceAll("_", " "), pos)){
										altSptPath.add(lastNode + "#" + pos + ";synonym of " + th.normalize(target, tPOS).replaceAll("_", " "));
										subpaths.push((Vector<String>) altSptPath.clone());
										match = true;											
										break;
									}
								}
	
								if (match)
									break;
							}
	
							if (!match){																					
								//Get all the roles linked to the this supertype, in the selected synsets
								List<String> allRoles = dg.getRolesBySupertype(bestSynsets, bestSupertype);
	
								//Get the most similar roles w.r.t. the target
								List<String> roles = new ArrayList<String>();
								for (String role : allRoles){ //strip off the role name
									roles.add(role.split(";")[0]);
								}
	
								List<String> bestRoles = getBestMatches(th.normalize(target, tPOS).replaceAll("_", " "), roles, true);
	
								for (int l=0; l < bestRoles.size(); l++){ //put the role name back
									for (String role : allRoles){
										if (role.startsWith(bestRoles.get(l) + ";")){
											bestRoles.set(l, role);
											break;
										}
									}
								}
	
								//Put the supertype in the path
								altSptPath.add(bestSupertype + "#" + pos + ";supertype of " + lastNode);
	
								//Work on roles linked to this supertype
								for (int j=0; j < bestRoles.size(); j++){
									Vector<String> newAltPath = (Vector<String>) altSptPath.clone();
	
									//Work on head words for this role
									if (!bestRoles.get(j).endsWith(";has_supertype")){
										newAltPath.add(bestRoles.get(j));
										String text = bestRoles.get(j).split(";")[0];
										
										try{
											List<String> chunks = th.split(text);
											List<String> headWords = getHeadWords(chunks, th.normalize(target, tPOS).replaceAll("_", " "), true);
		
											//Create a new path for each of the head words and put them on the stack
											for (int k=0; k < headWords.size(); k++){
												Vector<String> newAltWordPath = (Vector<String>)newAltPath.clone();
												newAltWordPath.add(headWords.get(k).replace(";", "#") + ";head");
		
												if (!subpaths.contains(newAltWordPath)){
													subpaths.push((Vector<String>)newAltWordPath.clone());													
												}
											}
										}
										catch (Exception e){
											e.printStackTrace();
										}
									}
									else{
										if (!subpaths.contains(newAltPath)){
											subpaths.push((Vector<String>)newAltPath.clone());	
												
										}	
									}
								}
							}
						}
	
						//Work on the first supertype
						match = false;
						String firstSupertype = bestSupertypes.get(0);
						List<Resource> bestSynsets = dg.getSynsetsBySupertype(synsets, firstSupertype);
	
						//Get all the synonyms to check whether one of them matches the target
						for (Resource synset : bestSynsets){
							List<String> synWords = dg.getSynonyms(synset);
	
							for (String synWord : synWords){
								if (areSynonyms(synWord, th.normalize(target, tPOS).replaceAll("_", " "), pos)){
									currentPath.add(nextNode.replaceAll("_", " ") + "#" + pos + ";synonym of " + th.normalize(target, tPOS).replaceAll("_", " "));
									match = true;									
									nextNode = th.normalize(target, tPOS);
									break;
								}
							}
	
							if (match)
								break;
						}
	
						if (!match){																					
							//Get all the roles linked to the this supertype, in the selected synsets
							List<String> allRoles = dg.getRolesBySupertype(bestSynsets, firstSupertype);
	
							//Get the most similar roles w.r.t. the target
							List<String> roles = new ArrayList<String>();
							for (String role : allRoles){ //strip off the role name
								roles.add(role.split(";")[0]);
							}
	
							List<String> bestRoles = getBestMatches(th.normalize(target, tPOS).replaceAll("_", " "), roles, false);
	
							for (int l=0; l < bestRoles.size(); l++){ //put the role name back
								for (String role : allRoles){
									if (role.startsWith(bestRoles.get(l) + ";")){
										bestRoles.set(l, role);
										break;
									}
								}
							}	
	
							//Put the supertype in the current path
							pathEnd = currentPath.get(currentPath.size()-1).split(";")[0];
							lastNode = pathEnd.contains("#") ? pathEnd.split("#")[0] : pathEnd;
							currentPath.add(firstSupertype + "#" + pos + ";supertype of " + lastNode);
	
							//Work on the first role and put all the other ones in the stack to be processed later								
							for (int j=1; j < bestRoles.size(); j++){
								Vector<String> altPath = (Vector<String>) currentPath.clone();
	
								//Get the role's head words and create a new path for each of them, to be processed later
								if (!bestRoles.get(j).endsWith(";has_supertype")){
									altPath.add(bestRoles.get(j));
									String text = bestRoles.get(j).split(";")[0];
									
									try {
										List<String> chunks = th.split(text);
										List<String> headWords = getHeadWords(chunks, th.normalize(target, tPOS).replaceAll("_", " "), true);
										
										for (int k=0; k < headWords.size(); k++){
											Vector<String> altWordPath = (Vector<String>)altPath.clone();
											altWordPath.add(headWords.get(k).replace(";", "#") + ";head");	
		
											if (!subpaths.contains(altWordPath)){
												subpaths.push((Vector<String>)altWordPath.clone());													
											}	
										}
									}
									catch (Exception e){
										e.printStackTrace();
									}
								}
								else{
									if (!subpaths.contains(altPath)){
										subpaths.push((Vector<String>)altPath.clone());
									}	
									
								}
							}
	
							//Analyze the first role
							String firstRole = bestRoles.get(0);
							String firstRoleText = firstRole.split(";")[0];
	
							if (!firstRole.endsWith(";has_supertype")){
								currentPath.add(firstRole);
								
								try {
									List<String> chunks = th.split(firstRoleText);								
									List<String> headWords = getHeadWords(chunks, th.normalize(target, tPOS).replaceAll("_", " "), false);
									
									//Work on the first head word and put all the other ones in the stack to be processed later
									if (headWords.size()  > 0){
										for (int k=1; k < headWords.size(); k++){
											Vector<String> altWordPath = (Vector<String>) currentPath.clone();
			
											altWordPath.add(headWords.get(k).replace(";", "#") + ";head");
			
											if (!subpaths.contains(altWordPath)){
												subpaths.push((Vector<String>)altWordPath.clone());												
											}
										}
			
										nextNode = headWords.get(0).split(";")[0].replaceAll(" ", "_");
										pos = headWords.get(0).split(";")[1];
										currentPath.add(headWords.get(0).replace(";", "#") + ";head");
									}	
								}
								catch (Exception e){
									e.printStackTrace();
								}
							}
							else{
								nextNode = th.normalize(firstRoleText, pos);	
							}
	
							if (nextNode.equals(th.normalize(target, tPOS))){
								match = true;										
							}
							depth = (depth != maxDepth + 1) ? getPathDepth(currentPath) : depth;
						}
					}
				}	
				else{
					//If no synsets were found and the role being analyzed is a supertype, it could have been 
					//misclassified (wrong combination of words); try again with its head words						
					if (currentRole.contains("supertype") && nextNode.contains("_") && !nextNode.equals(lastSynsetNotFound)){
						lastSynsetNotFound = nextNode;

						try {
							List<String> sptChunks = th.split(nextNode.replaceAll("_", " "));
							List<String> sptHeadWords = getHeadWords(sptChunks, th.normalize(target, tPOS).replaceAll("_", " "), false);

							//Work on the first head word and put all the other ones in the stack to be processed later
							if (sptHeadWords.size() > 0){
								for (int k=1; k < sptHeadWords.size(); k++){
									Vector<String> sptFixPath = (Vector<String>) currentPath.clone();

									sptFixPath.add(sptHeadWords.get(k).replace(";", "#") + ";supertype head");

									if (!subpaths.contains(sptFixPath)){
										subpaths.push((Vector<String>)sptFixPath.clone());							
									}
								}

								nextNode = sptHeadWords.get(0).split(";")[0].replaceAll(" ", "_");
								pos = sptHeadWords.get(0).split(";")[1];
								currentPath.add(sptHeadWords.get(0).replace(";", "#") + ";supertype head");

								if (nextNode.equals(th.normalize(target, tPOS))){
									match = true;								
								}
								depth = getPathDepth(currentPath);
							}	
						}
						catch (Exception e){
							e.printStackTrace();
						}
					}
					else{
						break;
					}	
				}
			}
			
			if (match){
				currentPath.add(target + ";target");
			}
			else{
				currentPath.add("null;null");
			}

			currentPath = cleanPath(currentPath);

			if (!paths.contains(currentPath)){
				paths.add((Vector<String>)currentPath.clone());

				if (currentPath.lastElement().equals(target + ";target")){
					targetReached = true;
				}
			}

			totalPathsTried++;

			//Stop if the max number of paths is reached and at least one valid path has already been found,
			//or if the search limit was reached (probably indicating that there's no valid path)
			if ((targetReached && totalPathsTried >= maxPaths) || totalPathsTried >= searchLimit){
				break;
			}
		}

		//Remove invalid paths
		Predicate<Vector<String>> invalid = v -> v.lastElement().equals("null;null");
		paths.removeIf(invalid);

		return paths;			
	}

	//Format the path that confirms the entailment to create a human-readable justification
	private String writeJustification(Vector<String> path){

		String justification = new String();
		String previousStep = path.get(0);
		String currentNode = path.get(0).split("#")[0];

		for (int i=0; i < path.size()-1; i++){
			String step = path.get(i);
			String node = step.split(";")[0].replaceAll("_", " ");
			String role = step.split(";")[1];
			String pos = node.contains("#") ? node.split("#")[1] : "";
			node = pos.equals("") ? node : node.replace("#" + pos, "");
			String nextStep = path.get(i+1);
			boolean vowel = false;

			if ((role.contains("supertype") && !nextStep.endsWith(";supertype head")) || role.equals("supertype head")){
				if (nextStep.contains(";supertype") || nextStep.contains(";synonym") || nextStep.contains(";target")){

					//Normalize supertype beginning
					node = node.startsWith("to ") ? node.replaceFirst("to ", "") : node;
					node = node.startsWith("a ") ? node.replaceFirst("a ", "") : node;
					node = node.startsWith("an ") ? node.replaceFirst("an ", "") : node;
					node = node.startsWith("the ") ? node.replaceFirst("the ", "") : node;

					vowel = (th.isVowel(currentNode.charAt(0))) ? true : false;
					justification += pos.startsWith("VB") ? ("To " + currentNode + " is ") 
							: ((vowel ? "An " : "A ") + currentNode + " is ");

					justification += pos.startsWith("VB") ? ("a way of " + (node.endsWith("ing") ? "" : "to ") + node + "\n") : ("a kind of " + node + "\n");
					currentNode = node;
				}
				else{
					vowel = (th.isVowel(currentNode.charAt(0))) ? true : false;
					justification += pos.startsWith("VB") ? ("To " + currentNode + " is ") 
							: ((vowel ? "An " : "A ") + currentNode + " is ");

					vowel = (th.isVowel(node.charAt(0))) ? true : false;
					justification += pos.startsWith("VB") ? ("to " + node + " ") : ((vowel ? "an " : "a ") + node + " ");
					currentNode = node;
				}
			}
			else if (role.contains("synonym")){
				justification += pos.startsWith("VB") 
						? ("To " + node + " is synonym of to " + role.replace("synonym of ", "") + "\n")
								: (th.capitalize(node) + " is " + role + "\n");
						currentNode = node;		
			}
			else if (! role.equals("source") && !role.equals("head") 
					&& !role.equals("target") && !role.contains("supertype")){
				justification += node + "\n";
			}
			else if (role.equals("head")){
				currentNode = node;
			}

			if  (!previousStep.contains(";source") && !nextStep.contains(";supertype head")){
				previousStep = step;
			}	
		}

		return justification;
	}
	
	//Compute and justify a single entailment by finding the best path among all paths found 
	//for all source-target pairs
	public EntailmentDecision computeEntailment(List<List<String>> pairs){
		
		EntailmentDecision decision = new EntailmentDecision();
		Vector<Vector<String>> paths = new Vector<Vector<String>>();
		
		decision.setModel("GraphNavigation");

		for (List<String> pair : pairs){
			String source = pair.get(0).split("#")[0];
			String sPOS = pair.get(0).split("#")[1];
			String target = pair.get(1).split("#")[0];
			String tPOS = pair.get(1).split("#")[1];
			
			paths.addAll(findPaths(source, sPOS, target, tPOS));
		}

		if (!paths.isEmpty()){
			Vector<String> bestPath = getShortestPath(paths);
			String justification = writeJustification(bestPath);
			
			decision.setDecision("yes");
			decision.setJustification(justification);
		}
		else{
			decision.setDecision("no");
			decision.setJustification("null");
		}
		return decision;
	}

}
