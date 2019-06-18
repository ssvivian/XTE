package graph;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.URIref;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public class DefinitionGraph {
	
	private Model m;
	private static final String sptProp = "http://nlp/resources/DefinitionSemanticRoles#has_supertype";
	private String Vnsp;
	private String Nnsp;
	private String Ensp;
	
	//Identify the namespaces for noun, verbs and general expressions
	private void setNamespaces(){
			
		Map<String, String> nsPrefixes = m.getNsPrefixMap();
		
		for(Iterator<String> i = nsPrefixes.keySet().iterator(); i.hasNext(); ){
			String namespace = (String) nsPrefixes.get(i.next());
				
			if (namespace.contains("Expression")){
				Ensp = namespace;
			}
			else if (namespace.contains("NounSynset")){
				Nnsp = namespace;
			}
			else if (namespace.contains("VerbSynset")){
				Vnsp = namespace;
			}
		}
	}
	
	public DefinitionGraph(String file){
		
		m = ModelFactory.createDefaultModel();		
		InputStream in = FileManager.get().open(file);
		
		m.read(in, null);
		        
		setNamespaces();
	}
	
	//Get the noun namespace
	public String getNounNamespace(){
		
		return Nnsp;
	}
	
	//Get the verb namespace
	public String getVerbNamespace(){
		
		return Vnsp;
	}
	
	//Get the expression namespace
	public String getExpressionNamespace(){
			
		return Ensp;
	}
	
	//Alternative to method getLocalName() that doesn't work properly when the name contains special characters
	public String getResourceName(Resource res){
			
		String uri = URIref.decode(res.getURI());
		String name = uri.substring(uri.indexOf('#')+1);
			
		return name;
	}
		
	//Alternative to method getNamespace() that doesn't work properly when the name contains special characters
	public String getResourceNamespace(Resource res){
				
		String uri = URIref.decode(res.getURI());
		String nsp = uri.substring(0, uri.indexOf('#')+1);
			
		return nsp;
	}
		
	//Get all entity nodes that contain a given word as a label
	public List<Resource> getSynsets (String word, String namespace){
		
		List<Resource> synsets = new ArrayList<Resource>();
		word = word.replaceAll(" ", "_").replaceAll("\\\\", "%5C").toLowerCase();
		
		//For faster search, words in the RDF labels must be lower case
		String queryString = "SELECT ?x WHERE {" +
                             "   ?x <http://www.w3.org/2000/01/rdf-schema#label> \"" + word + "\" . " +
                             "   FILTER(STRSTARTS(STR(?x), \"" + namespace + "\"))" + 
                             "}";

		Query query = QueryFactory.create(queryString);
		QueryExecution qe = QueryExecutionFactory.create(query, m);
		ResultSet results = qe.execSelect();
		
		while (results.hasNext()){
			Binding solution = results.nextBinding();
			Var x = solution.vars().next();
			Resource synset = m.getResource(solution.get(x).toString());
			synsets.add(synset);
		}
		
		return synsets;
	}
	
	//Get the synsets that have a specific supertype, from a list of synsets (and not from the whole model)
	public List<Resource> getSynsetsBySupertype(List<Resource> synsets, String supertype){
		
		List<Resource> selectedSynsets = new ArrayList<Resource>();
		supertype = supertype.replaceAll(" ", "_");
		
		for (Resource synset : synsets){
			StmtIterator it = synset.listProperties(m.getProperty(sptProp));
			while (it.hasNext()){
				Resource spt = (Resource) it.next().getObject();
				if (getResourceName(spt).equals(supertype)){
					selectedSynsets.add(synset);
				}
			}	
		}
		return selectedSynsets;
	}
	
	//Get all the synonyms of a word
	public List<String> getSynonyms (Resource synset){
		
		List<String> synonyms = new ArrayList<String>();
		StmtIterator it = synset.listProperties(RDFS.label);
		
		while (it.hasNext()){
			String synonym = it.next().getObject().toString();
			synonyms.add(synonym.replaceAll("_", " "));
		}
		
		return synonyms;
	}
	
	//Get all the supertypes of a set of synsets
	public List<Resource> getSupertypes (List<Resource> synsets){
		
		List<Resource> supertypes = new ArrayList<Resource>();
		
		for (Resource synset : synsets){
			StmtIterator it = synset.listProperties(m.getProperty(sptProp));
			
			while (it.hasNext()){
				Resource supertype = (Resource) it.next().getObject();
				supertypes.add(supertype);
			}
		}
		return supertypes;
	}
	
	//Get all the roles in a definition linked to a specific supertype
	public List<String> getRolesBySupertype (List<Resource> synsets, String suptp){
		
		List<String> roles = new ArrayList<String>();
		
		Resource supertype = m.getResource(Ensp + suptp.replaceAll(" ", "_"));
		roles.add(getResourceName(supertype).replaceAll("_", " ") + ";has_supertype");
		
		for (Resource synset : synsets){
			StmtIterator it = synset.listProperties(RDF.type);
			
			while (it.hasNext()){
				ResourceImpl roleStmt = (ResourceImpl) it.next().getObject();
				Resource spt = (Resource) roleStmt.getProperty(RDF.subject).getObject();
				
				if (spt.equals(supertype)){
					Resource predicate = (Resource) roleStmt.getProperty(RDF.predicate).getObject();
					
					if (roleStmt.getProperty(RDF.object).getObject().isLiteral()){
						String subject = roleStmt.getProperty(RDF.object).getObject().toString();
						roles.add(subject + ";" + getResourceName(predicate));
					}
					else{
						ResourceImpl objStmt = (ResourceImpl) roleStmt.getProperty(RDF.object).getObject();
						String reifSubject = getResourceName((Resource) objStmt.getProperty(RDF.subject).getObject()).replaceAll("_", " ");
						String reifObject = objStmt.getProperty(RDF.object).getObject().toString();
						Resource reifPredicate = (Resource) objStmt.getProperty(RDF.predicate).getObject();
						
						roles.add(reifSubject + ";" + getResourceName(predicate));
						roles.add(reifObject + ";" + getResourceName(reifPredicate));
					}
				}
			}
		}
		return roles;
	}
	
	//For each supertype in a list, get all the roles in a definition linked to it
	public Map<String, List<String>> listRolesBySupertype (List<Resource> synsets, List<Resource> supertypes){
		
		Map<String, List<String>> tuples = new HashMap<String, List<String>>();
		
		for (Resource supertype : supertypes){
			List<String> roles = getRolesBySupertype(synsets, getResourceName(supertype));
			
			if (tuples.containsKey(getResourceName(supertype))){
				List<String> allRoles = tuples.get(getResourceName(supertype));
				allRoles.addAll(roles);
				tuples.put(getResourceName(supertype), allRoles);
			}
			else{
				tuples.put(getResourceName(supertype), roles);
			}
		}
		return tuples;
	}
}
