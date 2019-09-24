# XTE - Explainable Text Entailment

The Explainable Text Entailment - XTE - is a syntactic-semantic composite interpretable approach for recognizing textual entailment that explores graph knowledge bases built from lexical dictionary definitions and generates natural language justifications to explain the entailment decision. 

The system employs different methods for recognizing the entailment depending on the phenomenom present in the T-H entailment pair: if T and H presents only a syntactic variation, the Tree Edit Distance model is used; if there is a semantic relationship between the sentence, then the Distributional Graph Navigation model is employed.

The approach is described in the following paper:

> Silva, V. S., Freitas, A., Handschuh, S. **Exploring Knowledge Graphs in an Interpretable Composite Approach for Text Entailment**. Thirty-Third AAAI Conference on Artificial Intelligence (AAAI-19). Honolulu, USA. 2019.

Example usage in the [Example.java](https://github.com/ssvivian/XTE/blob/master/src/test/java/entail/Example.java) class.

The WordNet definition graph is available in the [graphs](https://github.com/ssvivian/XTE/tree/master/graphs) directory, the full set of graphs can be found [here](https://figshare.com/collections/Definition_Knowledge_Graphs/4675841).
