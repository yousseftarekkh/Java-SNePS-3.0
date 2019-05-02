package sneps.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import sneps.exceptions.CannotInsertJustificationSupportException;
import sneps.exceptions.CustomException;
import sneps.exceptions.DuplicatePropositionException;
import sneps.exceptions.NodeNotFoundInNetworkException;
import sneps.exceptions.NodeNotFoundInPropSetException;
import sneps.exceptions.NotAPropositionNodeException;
import sneps.network.classes.Semantic;
import sneps.network.classes.setClasses.ChannelSet;
import sneps.network.classes.setClasses.NodeSet;
import sneps.network.classes.setClasses.PropositionSet;
import sneps.network.classes.setClasses.ReportSet;
import sneps.network.classes.term.Term;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import sneps.snebr.Context;
import sneps.snebr.Controller;
import sneps.snebr.Support;
import sneps.snip.InferenceTypes;
import sneps.snip.Pair;
import sneps.snip.Report;
import sneps.snip.Runner;
import sneps.snip.channels.AntecedentToRuleChannel;
import sneps.snip.channels.Channel;
import sneps.snip.channels.ChannelTypes;
import sneps.snip.channels.MatchChannel;
import sneps.snip.channels.RuleToConsequentChannel;
import sneps.snip.matching.LinearSubstitutions;
import sneps.snip.matching.Match;
import sneps.snip.matching.Matcher;
import sneps.snip.matching.Substitutions;

public class PropositionNode extends Node implements Serializable {
	private Support basicSupport;
	protected ChannelSet outgoingChannels;
	protected ChannelSet incomingChannels;
	protected ReportSet knownInstances;
	protected ReportSet newInstances;

	public PropositionNode() {
		outgoingChannels = new ChannelSet();
		incomingChannels = new ChannelSet();
		knownInstances = new ReportSet();
	}

	public PropositionNode(Term trm) {
		super(Semantic.proposition, trm);
		outgoingChannels = new ChannelSet();
		incomingChannels = new ChannelSet();
		knownInstances = new ReportSet();
		setTerm(trm);
	}

	/***
	 * Adding a report to all outgoing channels
	 * 
	 * @param report
	 */
	public void broadcastReport(Report report) {
		for (Channel outChannel : outgoingChannels) {
			try {
				if (outChannel.testReportToSend(report)) {
					// System.out.println("SENDING REPORT " + this);
				}
			} catch (NotAPropositionNodeException | NodeNotFoundInNetworkException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public boolean sendReport(Report report, Channel channel) {
		try {
			if (channel.testReportToSend(report)) {
				// System.out.println("SENDING REPORT " + this);
				return true;
			}
		} catch (NotAPropositionNodeException | NodeNotFoundInNetworkException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private void sendReports(NodeSet isAntecedentTo, ReportSet reports, ChannelTypes channelType,
			Channel currentChannel) {
		for (Node node : isAntecedentTo) {
			for (Report report : reports) {
				Substitutions reportSubs = report.getSubstitutions();
				Collection<PropositionSet> reportSuppSet = report.getSupports();
				boolean reportSign = report.getSign();
				String currentChannelContextName = currentChannel.getContextName();
				Channel newChannel = establishChannel(channelType, node, null, reportSubs, currentChannelContextName,
						InferenceTypes.FORWARD, -1);
				outgoingChannels.addChannel(newChannel);
				node.receiveReport(newChannel);
			}
		}

	}

	private void sendReports(List<Match> list, ReportSet reports, Channel currentChannel) {
		for (Match match : list) {
			for (Report report : reports) {
				Substitutions reportSubs = report.getSubstitutions();
				Collection<PropositionSet> reportSuppSet = report.getSupports();
				Node sentTo = match.getNode();
				boolean reportSign = report.getSign();
				String currentChannelContextName = currentChannel.getContextName();
				int matchType = match.getMatchType();
				Channel newChannel = establishChannel(ChannelTypes.MATCHED, sentTo, null, reportSubs,
						currentChannelContextName, InferenceTypes.FORWARD, matchType);
				outgoingChannels.addChannel(newChannel);
				sentTo.receiveReport(newChannel);
			}
		}
	}

	/***
	 * Method handling all types of Channels establishment according to different
	 * channel types passed through the matching.
	 * 
	 * @param type           type of channel being addressed
	 * @param currentElement source Node/Match element being addressed
	 * @param switchSubs     mapped substitutions from origin node
	 * @param filterSubs     constraints substitutions for a specific request
	 * @param contextName    context name used
	 * @param inferenceType  inference type used for this process
	 * @param matchType      int representing the match Type. -1 if not a matching
	 *                       node scenario
	 * @return the established type based channel
	 */
	private Channel establishChannel(ChannelTypes type, Object currentElement, Substitutions switchSubs,
			Substitutions filterSubs, String contextName, InferenceTypes inferenceType, int matchType) {
		boolean matchTypeEstablishing = currentElement instanceof Match;
		Node evaluatedReporter = matchTypeEstablishing ? ((Match) currentElement).getNode() : (Node) currentElement;
		Substitutions switchLinearSubs = switchSubs == null ? new LinearSubstitutions() : switchSubs;
		Channel newChannel;
		switch (type) {
		case MATCHED:
			newChannel = new MatchChannel(switchLinearSubs, filterSubs, contextName, this, evaluatedReporter, true,
					inferenceType, matchType);
			break;
		case RuleAnt:
			newChannel = new AntecedentToRuleChannel(switchLinearSubs, filterSubs, contextName, this, evaluatedReporter,
					true, inferenceType);
		default:
			newChannel = new RuleToConsequentChannel(switchLinearSubs, filterSubs, contextName, this, evaluatedReporter,
					true, inferenceType);
		}
		return newChannel;

	}

	/***
	 * Helper method responsible for establishing channels between this current node
	 * and each of the List<Match> to further request instances with the given
	 * inputs
	 * 
	 * @param list
	 * @param contextId
	 * @param inferenceType
	 */
	protected void sendRequestsToMatches(List<Match> list, String contextId, InferenceTypes inferenceType) {
		for (Match currentMatch : list) {
			Substitutions switchSubs = currentMatch.getSwitchSubs();
			Substitutions filterSubs = currentMatch.getFilterSubs();
			int matchType = currentMatch.getMatchType();
			Channel newChannel = establishChannel(ChannelTypes.MATCHED, currentMatch, switchSubs, filterSubs, contextId,
					inferenceType, matchType);
			incomingChannels.addChannel(newChannel);
			currentMatch.getNode().receiveRequest(newChannel);
		}
	}

	/***
	 * Helper method responsible for establishing channels between this current node
	 * and each of the NodeSet to further request instances with the given inputs
	 * 
	 * @param ns            NodeSet to be sent to
	 * @param filterSubs    Substitutions to be passed
	 * @param contextID     latest channel context
	 * @param channelType
	 * @param inferenceType
	 */
	protected void sendRequestsToNodeSet(NodeSet ns, Substitutions filterSubs, String contextID,
			ChannelTypes channelType, InferenceTypes inferenceType) {
		for (Node sentTo : ns) {
			Channel newChannel = establishChannel(channelType, sentTo, null, filterSubs, contextID, inferenceType, -1);
			incomingChannels.addChannel(newChannel);
			sentTo.receiveRequest(newChannel);
		}
	}

	public void processRequests() {
		for (Channel outChannel : outgoingChannels)
			try {
				processSingleRequestsChannel(outChannel);
			} catch (NotAPropositionNodeException | NodeNotFoundInNetworkException e) {
				e.printStackTrace();
			} catch (DuplicatePropositionException e) {
				e.printStackTrace();
			}
	}

	/***
	 * Request handling in Non-Rule proposition nodes.
	 * 
	 * @param currentChannel
	 * @throws NodeNotFoundInNetworkException
	 * @throws NotAPropositionNodeException
	 * @throws DuplicatePropositionException
	 */
	protected void processSingleRequestsChannel(Channel currentChannel)
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException, DuplicatePropositionException {
		// TODO check correctness
		int instanceNodeId = getId();
		PropositionSet propSet = new PropositionSet();
		propSet.add(instanceNodeId);
		Collection<PropositionSet> nodeAssumptionBasedSupport = getAssumptionBasedSupport().values();
		String currentContextName = currentChannel.getContextName();
		Context desiredContext = Controller.getContextByName(currentContextName);
		if (assertedInContext(desiredContext)) {
			// TODO change the subs to hashsubs
			Collection<PropositionSet> support = new ArrayList<PropositionSet>();
			support.add(propSet);
			Report reply = new Report(new LinearSubstitutions(), nodeAssumptionBasedSupport, true);
			knownInstances.addReport(reply);
			broadcastReport(reply);
		} else {
			boolean sentAtLeastOne = false;
			for (Report currentReport : knownInstances)
				sentAtLeastOne |= sendReport(currentReport, currentChannel);
			Substitutions filterSubs = currentChannel.getFilter().getSubstitutions();
			if (!sentAtLeastOne || isWhQuestion(filterSubs)) {
				NodeSet dominatingRules = getDominatingRules();
				NodeSet toBeSentToDom = removeAlreadyWorkingOn(dominatingRules, currentChannel, false);
				sendRequestsToNodeSet(toBeSentToDom, new LinearSubstitutions(), currentContextName,
						ChannelTypes.RuleAnt, currentChannel.getInferenceType());
				if (!(currentChannel instanceof MatchChannel)) {
					List<Match> matchingNodes = Matcher.match(this);
					List<Match> toBeSentToMatch = removeAlreadyWorkingOn(matchingNodes, currentChannel, false);
					sendRequestsToMatches(toBeSentToMatch, currentContextName, currentChannel.getInferenceType());
				}
				/*
				 * if (!(currentChannel instanceof MatchChannel)) // was in !alreadyWorking if
				 * condition getNodesToSendRequests(ChannelTypes.MATCHED,
				 * currentChannel.getContextName(), null, currentChannel.getInferenceType());
				 */
			}
		}
	}

	public void processReports() {
		for (Channel inChannel : incomingChannels)
			processSingleReportsChannel(inChannel);
	}

	/***
	 * Report handling in Non-Rule proposition nodes.
	 * 
	 * @param currentChannel
	 */
	protected void processSingleReportsChannel(Channel currentChannel) {
		ReportSet reports = currentChannel.getReportsBuffer();
		for (Report currentReport : reports) {
			Substitutions reportSubs = currentReport.getSubstitutions();
			Collection<PropositionSet> reportSupportSet = currentReport.getSupports();
			boolean reportSign = currentReport.isPositive();
			String currentChannelContextName = currentChannel.getContextName();
			boolean toBeSentFlag = true;
			if (currentChannel instanceof MatchChannel) {
				int channelMatchType = ((MatchChannel) currentChannel).getMatchType();
				toBeSentFlag = (channelMatchType == 0) || (channelMatchType == 1 && currentReport.isPositive())
						|| (channelMatchType == 2 && currentReport.isNegative());
			}
			Report alteredReport = new Report(reportSubs, reportSupportSet, reportSign);
			if (knownInstances.contains(alteredReport))
				continue;
			if (toBeSentFlag)
				broadcastReport(alteredReport);
		}
		/* Handling forward inference broadcasting */
		if (currentChannel.getInferenceType() == InferenceTypes.FORWARD) {
			List<Match> matchesReturned = Matcher.match(this);
			if (matchesReturned != null)
				sendReports(matchesReturned, reports, currentChannel);
			NodeSet isAntecedentTo = getDominatingRules();
			sendReports(isAntecedentTo, reports, ChannelTypes.RuleAnt, currentChannel);
		}
		currentChannel.clearReportsBuffer();
	}

	// PROCESS REPORT : 3adi -> , forward
	// -> same as 3adi , plus matching to send and get the nodes el howa lihom
	// antecedents we send reports

	/***
	 * 
	 * @param desiredContext
	 * @return whether the PropositionNode is asserted in a desiredContext or not
	 * @throws NodeNotFoundInNetworkException
	 * @throws NotAPropositionNodeException
	 */
	public boolean assertedInContext(Context desiredContext)
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		return desiredContext.isAsserted(this);
	}

	public boolean assertedInContext(String desiredContextName)
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		return Controller.getContextByName(desiredContextName).isAsserted(this);
	}

	/***
	 * Requests received added to the low priority queue to be served accordingly
	 * through the runner.
	 */
	public void receiveRequest(Channel channel) {
		outgoingChannels.addChannel(channel);
		Runner.addToLowQueue(this);
		channel.setRequestProcessed(true);
	}

	/***
	 * Reports received added to the high priority queue to be served accordingly
	 * through the runner.
	 */
	public void receiveReports(Channel channel) {
		outgoingChannels.addChannel(channel);
		Runner.addToHighQueue(this);
	}

	public void deduce() {
		Runner.initiate();
		String currentContextName = Controller.getCurrentContextName();
		getNodesToSendRequests(ChannelTypes.RuleCons, currentContextName, null, InferenceTypes.BACKWARD);
		getNodesToSendRequests(ChannelTypes.MATCHED, currentContextName, null, InferenceTypes.BACKWARD);
		Runner.run(); // what to return here ?
	}

	public void add() {

	}

	/***
	 * Method handling all types of Nodes retrieval and sending different type-based
	 * requests to each Node Type
	 * 
	 * @param type               type of channel being addressed
	 * @param currentContextName context name used
	 * @param substitutions      channel substitutions applied over the channel
	 * @param inferenceType      inference type used for this process
	 */
	protected void getNodesToSendRequests(ChannelTypes channelType, String currentContextName,
			Substitutions substitutions, InferenceTypes inferenceType) {
		try {
			switch (channelType) {
			case MATCHED:
				List<Match> matchesReturned = Matcher.match(this);
				if (matchesReturned != null)
					sendRequestsToMatches(matchesReturned, currentContextName, inferenceType);
				break;
			case RuleCons:
				NodeSet dominatingRules = getDominatingRules();
				// TODO Youssef: check if passing a new LinearSubstitutions is correct
				Substitutions linearSubs = substitutions == null ? new LinearSubstitutions() : substitutions;
				sendRequestsToNodeSet(dominatingRules, linearSubs, currentContextName, channelType, inferenceType);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/***
	 * Method comparing opened incoming channels over each match's node of the
	 * matches whether a more generic request of the specified channel was
	 * previously sent in order not to re-send redundant requests -- ruleType gets
	 * applied on Andor or Thresh part.
	 * 
	 * @param matchingNodes
	 * @param currentChannel
	 * @param ruleType
	 * @return
	 */
	protected List<Match> removeAlreadyWorkingOn(List<Match> matchingNodes, Channel currentChannel, boolean ruleType) {
		List<Match> nodesToConsider = new ArrayList<Match>();
		for (Match sourceMatch : matchingNodes) {
			Node sourceNode = sourceMatch.getNode();
			if (sourceNode instanceof PropositionNode) {
				boolean conditionMet = ruleType && sourceNode == currentChannel.getRequester();
				if (!conditionMet) {
					conditionMet = true;
					Substitutions currentChannelFilterSubs = currentChannel.getFilter().getSubstitutions();
					ChannelSet outgoingChannels = ((PropositionNode) sourceNode).getOutgoingChannels();
					ChannelSet filteredChannelsSet = outgoingChannels.getFilteredRequestChannels(true);
					for (Channel outgoingChannel : filteredChannelsSet) {
						Substitutions processedChannelFilterSubs = outgoingChannel.getFilter().getSubstitutions();
						conditionMet &= !processedChannelFilterSubs.isSubSet(currentChannelFilterSubs)
								&& outgoingChannel.getRequester() == currentChannel.getReporter();
					}
					if (conditionMet)
						nodesToConsider.add(sourceMatch);
				}
			}
		}
		return nodesToConsider;
	}

	/***
	 * Method comparing opened incoming channels over each node of the nodes whether
	 * a more generic request of the specified channel was previously sent in order
	 * not to re-send redundant requests -- ruleType gets applied on Andor or Thresh
	 * part.
	 * 
	 * @param node    set on which we will check existing request
	 * @param channel current channel handling the current request
	 * @return NodeSet containing all nodes that has not previously requested the
	 *         subset of the specified channel request
	 */
	protected static NodeSet removeAlreadyWorkingOn(NodeSet nodes, Channel channel, boolean ruleType) {
		NodeSet nodesToConsider = new NodeSet();
		for (Node sourceNode : nodes)
			if (sourceNode instanceof PropositionNode) {
				boolean conditionMet = ruleType && sourceNode == channel.getRequester();
				if (!conditionMet) {
					conditionMet = true;
					Substitutions currentChannelFilterSubs = channel.getFilter().getSubstitutions();
					ChannelSet outgoingChannels = ((PropositionNode) sourceNode).getOutgoingChannels();
					ChannelSet filteredChannelsSet = outgoingChannels.getFilteredRequestChannels(true);
					for (Channel outgoingChannel : filteredChannelsSet) {
						Substitutions processedChannelFilterSubs = outgoingChannel.getFilter().getSubstitutions();
						conditionMet &= !processedChannelFilterSubs.isSubSet(currentChannelFilterSubs)
								&& outgoingChannel.getRequester() == channel.getReporter();
					}
					if (conditionMet)
						nodesToConsider.addNode(sourceNode);
				}
			}
		return nodesToConsider;
	}

	public Support getBasicSupport() {
		return basicSupport;
	}

	public void setBasicSupport() throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		this.basicSupport = new Support(this.getId());
	}

	public ChannelSet getOutgoingChannels() {
		return outgoingChannels;
	}

	public void setOutgoingChannels(ChannelSet outgoingChannels) {
		this.outgoingChannels = outgoingChannels;
	}

	public ChannelSet getIncomingChannels() {
		return incomingChannels;
	}

	public void setIncomingChannels(ChannelSet incomingChannels) {
		this.incomingChannels = incomingChannels;
	}

	public ReportSet getKnownInstances() {
		return knownInstances;
	}

	public void setKnownInstances(ReportSet knownInstances) {
		this.knownInstances = knownInstances;
	}

	public Hashtable<String, PropositionSet> getAssumptionBasedSupport() {
		return basicSupport.getAssumptionBasedSupport();

	}

	public Hashtable<String, PropositionSet> getJustificationSupport()
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		return basicSupport.getJustificationSupport();
	}

	public void addJustificationBasedSupport(PropositionSet propSet)
			throws NodeNotFoundInPropSetException, NotAPropositionNodeException, NodeNotFoundInNetworkException,
			DuplicatePropositionException, CannotInsertJustificationSupportException {
		basicSupport.addJustificationBasedSupport(propSet);
	}

	public void removeNodeFromSupports(PropositionNode propNode)
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		basicSupport.removeNodeFromSupports(propNode);

	}

	public void addParentNode(int id)
			throws DuplicatePropositionException, NotAPropositionNodeException, NodeNotFoundInNetworkException {
		basicSupport.addParentNode(id);

	}

	public ArrayList<Integer> getParentSupports() {
		return basicSupport.getParentSupports();
	}

	public boolean HasChildren() {
		return basicSupport.HasChildren();
	}

	public ArrayList<ArrayList<ArrayList<Integer>>> getMySupportsTree()
			throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		return basicSupport.getMySupportsTree();
	}

	public boolean reStructureJustifications() throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		return basicSupport.reStructureJustifications();
	}

	public void setHyp(boolean isHyp) throws NotAPropositionNodeException, NodeNotFoundInNetworkException {
		basicSupport.setHyp(isHyp);
	}
}