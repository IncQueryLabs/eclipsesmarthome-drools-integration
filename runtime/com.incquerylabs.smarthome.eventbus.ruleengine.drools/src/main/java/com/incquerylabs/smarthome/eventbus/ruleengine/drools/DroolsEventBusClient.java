package com.incquerylabs.smarthome.eventbus.ruleengine.drools;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.incquerylabs.smarthome.eventbus.api.DrlConfiguration;
import com.incquerylabs.smarthome.eventbus.api.DtableConfiguration;
import com.incquerylabs.smarthome.eventbus.api.IEventPublisher;
import com.incquerylabs.smarthome.eventbus.api.IEventSubscriber;
import com.incquerylabs.smarthome.eventbus.api.IRuleLoader;
import com.incquerylabs.smarthome.eventbus.api.RuleTemplateConfiguration;
import com.incquerylabs.smarthome.eventbus.api.events.GroupItemStateChangedEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemAddedEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemCommandEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemCommandHistory;
import com.incquerylabs.smarthome.eventbus.api.events.ItemRemovedEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemStateChangedEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemStateChangedHistory;
import com.incquerylabs.smarthome.eventbus.api.events.ItemStateEvent;
import com.incquerylabs.smarthome.eventbus.api.events.ItemUpdatedEvent;

public class DroolsEventBusClient implements IEventSubscriber {
	static Logger logger = LoggerFactory.getLogger(DroolsEventBusClient.class);
	private static final String subscriberName = "Drools event bus client ";

	private ConcurrentHashMap<String, FactHandle> addedItems = new ConcurrentHashMap<String, FactHandle>();
	private IRuleLoader ruleLoader = null;

	private KieSession kSession;
	private volatile boolean droolsInitialized = false;

	private IEventPublisher eventPublisher = null;

	@Override
	public void stateUpdated(ItemStateEvent itemStateEvent) {
		if (droolsInitialized) {
			logger.trace(itemStateEvent.toString());
		}
	}
	
	@Override
	public void stateChanged(ItemStateChangedEvent itemStateChangedEvent) {
		if (droolsInitialized) {
			changeStateInRuleEngine(itemStateChangedEvent);
			logger.info(subscriberName + itemStateChangedEvent);
		}
	}

	@Override
	public void groupStateChanged(GroupItemStateChangedEvent groupItemStateChangedEvent) {
		if (droolsInitialized) {
			changeStateInRuleEngine(groupItemStateChangedEvent);
			logger.info(subscriberName + groupItemStateChangedEvent);
		}
	}

	@Override
	public void commandReceived(ItemCommandEvent itemCommandEvent) {
		if (droolsInitialized) {
			addItemCommandToRuleEngine(itemCommandEvent);
			logger.debug(subscriberName + itemCommandEvent);
		}
	}

	@Override
	public void initItems(Collection<Item> items) {
		if (droolsInitialized) {
			logger.debug(subscriberName + " initializing " + items.size() + " items");
			for (Item item : items) {
				addItemToRuleEngine(item);
			}
		}
	}

	@Override
	public void itemAdded(ItemAddedEvent itemAddedEvent) {
		if (droolsInitialized) {
			addItemToRuleEngine(itemAddedEvent.getItem());
		}
	}

	@Override
	public void itemRemoved(ItemRemovedEvent itemRemovedEvent) {
		if (droolsInitialized) {
			removeItemFromRuleEngine(itemRemovedEvent.getName());
		}
	}

	@Override
	public void itemUpdated(ItemUpdatedEvent itemUpdatedEvent) {
		if (droolsInitialized) {
			removeItemFromRuleEngine(itemUpdatedEvent.getOldItemName());
			addItemToRuleEngine(itemUpdatedEvent.getNewItem());
		}
	}

	private void changeStateInRuleEngine(ItemStateChangedEvent itemStateChangedEvent) {
		updateItemInRuleEngine(itemStateChangedEvent.getItem());

		FactHandle handle = kSession.insert(itemStateChangedEvent);
		kSession.fireAllRules();
		kSession.delete(handle);
		kSession.insert(new ItemStateChangedHistory(itemStateChangedEvent));
	}

	private void addItemCommandToRuleEngine(ItemCommandEvent itemCommandEvent) {
		FactHandle handle = kSession.insert(itemCommandEvent);
		kSession.fireAllRules();
		kSession.delete(handle);
		kSession.insert(new ItemCommandHistory(itemCommandEvent));
	}

	private void addItemToRuleEngine(Item item) {
		if (addedItems.get(item.getName()) == null) {

			FactHandle handle = kSession.insert(item);
			addedItems.put(item.getName(), handle);

			logger.trace(subscriberName + "added item to rule engine: " + item.getName());

		} else {
			updateItemInRuleEngine(item);
		}
		kSession.fireAllRules();
	}

	private void removeItemFromRuleEngine(String itemName) {
		FactHandle handle = addedItems.get(itemName);

		if (handle != null) {
			kSession.delete(handle);
			kSession.fireAllRules();

			logger.trace(subscriberName + "removed item from rule engine: " + itemName);
		} else {
			logger.error(subscriberName + "tried to delete item" + itemName + ", but it wasn't in the rule engine");
		}
	}

	private void updateItemInRuleEngine(Item item) {
		kSession.update(addedItems.get(item.getName()), item);
		logger.info(subscriberName + "item " + item.getName() + " updated reference in the rule engine");
	}

	public void setEventPublisher(IEventPublisher eventPublisher) {
		if (droolsInitialized) {
			kSession.setGlobal("openhab", eventPublisher);
		} else {
			this.eventPublisher = eventPublisher;
		}
	}

	public void unsetEventPublisher(IEventPublisher eventPublisher) {
		kSession.setGlobal("openhab", null);
	}

	public void setRuleLoader(IRuleLoader ruleLoader) {
		this.ruleLoader = ruleLoader;
		loadDrools();
	}

	public void unsetRuleLoader(IRuleLoader ruleLoader) {
		this.ruleLoader = null;
	}

	@Override
	public String getSubscriberName() {
		return subscriberName;
	}

	private void addDrls(KnowledgeBuilder kbuilder) {

		List<DrlConfiguration> drls = ruleLoader.getDrls();
		if (drls != null) {
			for (DrlConfiguration drlConf : drls) {
				kbuilder.add(ResourceFactory.newInputStreamResource(drlConf.getDrl()).setSourcePath(drlConf.getPath()),
						ResourceType.DRL);
			}
		}

		List<DtableConfiguration> dtables = ruleLoader.getDtables();
		if (dtables != null) {
			for (DtableConfiguration dtableConf : dtables) {
				kbuilder.add(ResourceFactory.newInputStreamResource(dtableConf.getDtable())
						.setSourcePath(dtableConf.getPath()), ResourceType.DTABLE);
			}
		}

		List<RuleTemplateConfiguration> ruleTeamplates = ruleLoader.getRuleTemplates();
		if (ruleTeamplates != null) {
			for (RuleTemplateConfiguration ruleTeamplateConf : ruleTeamplates) {
				for (DrlConfiguration drlConf : ruleTeamplateConf.getTemplateRules()) {
					DecisionTableConfiguration dtableconfiguration = KnowledgeBuilderFactory
							.newDecisionTableConfiguration();

					dtableconfiguration.setInputType(DecisionTableInputType.XLSX);
					dtableconfiguration.setWorksheetName(ruleTeamplateConf.getWorksheetName());
					dtableconfiguration.addRuleTemplateConfiguration(
							ResourceFactory.newInputStreamResource(drlConf.getDrl()).setSourcePath(drlConf.getPath()),
							ruleTeamplateConf.getStartRow(), ruleTeamplateConf.getStartColumn());

					kbuilder.add(ResourceFactory.newInputStreamResource(ruleTeamplateConf.getTemplateData())
							.setSourcePath(ruleTeamplateConf.getPath()), ResourceType.DTABLE, dtableconfiguration);
				}
			}
		}
	}

	private void loadDrools() {
		try {
			KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
			addDrls(kbuilder);

			kSession = kbuilder.newKieBase().newKieSession();
			initGlobals();
			droolsInitialized = true;
			logger.debug(subscriberName + "successfully loaded DRL file");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initGlobals() {
		kSession.setGlobal("ON", OnOffType.ON);
		kSession.setGlobal("OFF", OnOffType.OFF);
		kSession.setGlobal("OPEN", OpenClosedType.OPEN);
		kSession.setGlobal("CLOSED", OpenClosedType.CLOSED);
		kSession.setGlobal("ZERO", PercentType.ZERO);
		kSession.setGlobal("HUNDRED", PercentType.HUNDRED);
		kSession.setGlobal("INCREASE", IncreaseDecreaseType.INCREASE);
		kSession.setGlobal("DECREASE", IncreaseDecreaseType.DECREASE);

		if (eventPublisher != null) {
			kSession.setGlobal("openhab", eventPublisher);
		}
	}
}
