package com.incquerylabs.smarthome.viatra.rules

import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher
import org.eclipse.viatra.query.runtime.api.IPatternMatch
import org.eclipse.viatra.transformation.runtime.emf.rules.batch.BatchTransformationRule
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine
import org.eclipse.viatra.transformation.runtime.emf.modelmanipulation.SimpleModelManipulations
import org.eclipse.viatra.transformation.runtime.emf.modelmanipulation.IModelManipulations
import com.incquerylabs.smarthome.viatra.SmartHomeRulesMatcher
import org.eclipse.viatra.transformation.runtime.emf.rules.batch.BatchTransformationRuleFactory
import org.apache.log4j.Logger
import com.incquerylabs.smarthome.viatra.SimpleRulesMatcher
import com.incquerylabs.smarthome.viatra.FilterRulesMatcher

class RuleProvider {
    
    extension IModelManipulations manipulation
    extension BatchTransformationRuleFactory = new BatchTransformationRuleFactory
    extension Logger logger = Logger.getLogger("smarthome.viatra")
    
    BatchTransformationRule<? extends IPatternMatch, ? extends ViatraQueryMatcher<?>> smarthomeRule
    BatchTransformationRule<? extends IPatternMatch, ? extends ViatraQueryMatcher<?>> simpleRule
    BatchTransformationRule<? extends IPatternMatch, ? extends ViatraQueryMatcher<?>> filterRule
    
    
    ViatraQueryEngine engine
    
    new(ViatraQueryEngine engine) {
        this.engine = engine
        manipulation = new SimpleModelManipulations(engine)
    }
    
    
    public def getSmarthomeRule() {
        if (smarthomeRule === null) {
            smarthomeRule = createRule.name("SmarthomeRule").precondition(SmartHomeRulesMatcher.querySpecification).action[
                val smarthomeName = it.smarthome.name
                val numRules = it.smarthome.rules.size
                
               
                debug('''Smarthome name: «smarthomeName»''')
                debug('''Smarthome number of rules: «numRules»''')
            ].build
        }
        return smarthomeRule
    }
    
    
    public def getSimpleRule() {
        if (simpleRule === null) {
            simpleRule = createRule.name("SimpleRule").precondition(SimpleRulesMatcher.querySpecification).action[
                val node = it.evaluatingNode
                
                debug('''
                package homeioexample;
                
                rule "Generated simple rule name here"
                    when 
                         «FOR command : node.commands»
                         Item( name == "«command.item.name»" )
                         «ENDFOR»
                         
                         ItemStateChaneEvent( 
                         «FOR event : node.events SEPARATOR ' || '»
                         ( name == "«event.item.name»" && state == «event.newState.state» )
                         «ENDFOR» 
                         )
                         
                     then
                        «FOR command : node.commands»
                        openhab.postCommand("«command.item.name»", «command.command»);
                        «ENDFOR»
                end
                ''')
            ].build
        }
        return simpleRule
    }
    
    
    public def getFilterRule() {
        if (filterRule === null) {
            filterRule = createRule.name("FilterRule").precondition(FilterRulesMatcher.querySpecification).action[
                val node = it.evaluatingNode
                
                debug('''
                package homeioexample;
                
                rule "Generated filter rule name here"
                    when 
                         «FOR command : node.commands»
                         Item( name == "«command.item.name»" )
                         «ENDFOR»
                         
                         «FOR filter : node.filters»
                         Item( name == "«filter.item.name»", state == «filter.requiredState.state» )
                         «ENDFOR»
                         
                         ItemStateChaneEvent( 
                         «FOR event : node.events SEPARATOR ' || '»
                         ( name == "«event.item.name»" && state == «event.newState.state» )
                         «ENDFOR» 
                         )
                         
                     then
                        «FOR command : node.commands»
                        openhab.postCommand("«command.item.name»", «command.command»);
                        «ENDFOR»
                end
                ''')
                
            ].build
        }
        return filterRule
    }
    
}