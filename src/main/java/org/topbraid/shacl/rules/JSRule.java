package org.topbraid.shacl.rules;

import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.topbraid.shacl.js.JSGraph;
import org.topbraid.shacl.js.JSScriptEngine;
import org.topbraid.shacl.js.NashornUtil;
import org.topbraid.shacl.js.SHACLScriptEngineManager;
import org.topbraid.shacl.js.model.JSFactory;
import org.topbraid.shacl.model.SHJSExecutable;
import org.topbraid.shacl.vocabulary.SH;
import org.topbraid.spin.progress.ProgressMonitor;
import org.topbraid.spin.util.ExceptionUtil;
import org.topbraid.spin.util.JenaUtil;

class JSRule extends Rule {
	
	
	JSRule(Resource rule) {
		super(rule);
	}
	

	@Override
	public void execute(RuleEngine ruleEngine, List<RDFNode> focusNodes) {

		Resource rule = getResource();
		String functionName = JenaUtil.getStringProperty(rule, SH.jsFunctionName);
		if(functionName == null) {
			throw new IllegalArgumentException("Missing JavaScript function name at rule " + rule);
		}
		
		ProgressMonitor monitor = ruleEngine.getProgressMonitor();
		for(RDFNode focusNode : focusNodes) {
			
			if(monitor != null && monitor.isCanceled()) {
				return;
			}
			
			boolean nested = SHACLScriptEngineManager.begin();
			JSScriptEngine engine = SHACLScriptEngineManager.getCurrentEngine();
	
			SHJSExecutable as = rule.as(SHJSExecutable.class);
			JSGraph dataJSGraph = new JSGraph(ruleEngine.getDataset().getDefaultModel().getGraph(), engine);
			JSGraph shapesJSGraph = new JSGraph(ruleEngine.getDataset().getDefaultModel().getGraph(), engine);
			try {
				engine.executeLibraries(as);
				engine.put(SH.JS_DATA_VAR, dataJSGraph);
				engine.put(SH.JS_SHAPES_VAR, shapesJSGraph);
				
				QuerySolutionMap bindings = new QuerySolutionMap();
				bindings.add(SH.thisVar.getVarName(), focusNode);
				Object result = engine.invokeFunction(functionName, bindings);
				if(NashornUtil.isArray(result)) {
					for(Object tripleO : NashornUtil.asArray(result)) {
						if(NashornUtil.isArray(tripleO)) {
							Object[] nodes = NashornUtil.asArray(tripleO);
							Node subject = JSFactory.getNode(nodes[0]);
							Node predicate = JSFactory.getNode(nodes[1]);
							Node object = JSFactory.getNode(nodes[2]);
							ruleEngine.infer(Triple.create(subject, predicate, object));
						}
						else {
							@SuppressWarnings("rawtypes")
							Map triple = (Map) tripleO;
							Node subject = JSFactory.getNode(triple.get("subject"));
							Node predicate = JSFactory.getNode(triple.get("predicate"));
							Node object = JSFactory.getNode(triple.get("object"));
							ruleEngine.infer(Triple.create(subject, predicate, object));
						}
					}
				}
			}
			catch(ScriptException ex) {
				ExceptionUtil.throwUnchecked(ex);
			}
			catch(Exception ex) {
				ex.printStackTrace();
				throw new ExprEvalException(ex);
			}
			finally {
				dataJSGraph.close();
				SHACLScriptEngineManager.end(nested);
			}
		}
	}
	
	
	public String toString() {
		String label = getLabel();
		if(label == null) {
			Statement s = getResource().getProperty(SH.jsFunctionName);
			if(s != null && s.getObject().isLiteral()) {
				label = s.getString();
			}
			else {
				label = "(Missing JavaScript function name)";
			}
		}
		return getLabelStart("JavaScript") + label;
	}
}
