package org.panda.causalpath.run;

import org.panda.causalpath.analyzer.CausalitySearcher;
import org.panda.causalpath.analyzer.ThresholdDetector;
import org.panda.causalpath.data.ActivityData;
import org.panda.causalpath.data.ProteinData;
import org.panda.causalpath.network.GraphWriter;
import org.panda.causalpath.network.Relation;
import org.panda.causalpath.network.RelationAndSelectedData;
import org.panda.causalpath.resource.ProteomicsFileReader;
import org.panda.causalpath.resource.ProteomicsLoader;
import org.panda.causalpath.resource.NetworkLoader;
import org.panda.resource.PhosphoSitePlus;
import org.panda.resource.ResourceDirectory;
import org.panda.resource.tcga.ProteomicsFileRow;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This class reads the proteomics platform and data files, and generates a ChiBE SIF graph.
 *
 * @author Ozgun Babur
 */
public class RPPAFrontFace
{
	/**
	 * Reads the RPPA platform and data files, and generates a ChiBE SIF graph.
	 *
	 * @param platformFile Name of the antibody reference file
	 * @param idColumn Column name of IDs
	 * @param symbolsColumn Column name of gene symbols
	 * @param sitesColumn Column name for phosphorylation sites
	 * @param effectColumn Column name for effect of the site on activity
	 * @param valuesFile Name of the measurements file
	 * @param valueColumn Name of the values column in the measurements file
	 * @param valueThreshold The value threshold to be considered as significant
	 * @param graphType Either "compatible" or "conflicting"
	 * @param siteMatchStrict option to enforce matching a phosphorylation site in the network with
	 *                       the annotation of antibody
	 * @param geneCentric Option to produce a gene-centric or an antibody-centric graph
	 * @param addInUnknownEffects Option to add the phospho sites with unknown effects as potential cause or conflict
	 * @param outputFilePrefix If the user provides xxx, then xxx.sif and xxx.format are generated
	 * @param customNetworkDirectory The directory that the network will be downloaded and SignedPC
	 *                               directory will be created in. Pass null to use default
	 * @throws IOException
	 */
	public static void generateRPPAGraphs(String platformFile, String idColumn,
		String symbolsColumn, String sitesColumn, String effectColumn, String valuesFile,
		String valueColumn, double valueThreshold, String graphType, boolean siteMatchStrict,
		int siteMatchProximityThreshold, int siteEffectProximityThreshold, boolean geneCentric,
		boolean addInUnknownEffects, String outputFilePrefix, String customNetworkDirectory) throws IOException
	{
		if (customNetworkDirectory != null) ResourceDirectory.set(customNetworkDirectory);

		// Read platform file
		List<ProteomicsFileRow> rows = ProteomicsFileReader.readAnnotation(platformFile, idColumn, symbolsColumn,
			sitesColumn, effectColumn);

		// Read values
		List<String> vals = Collections.singletonList(valueColumn);
		ProteomicsFileReader.addValues(rows, valuesFile, idColumn, vals, 0D);

		// Fill-in missing effect from PhosphoSitePlus
		PhosphoSitePlus.get().fillInMissingEffect(rows, siteEffectProximityThreshold);

		generateRPPAGraphs(rows, valueThreshold, graphType, siteMatchStrict, siteMatchProximityThreshold, geneCentric,
			addInUnknownEffects, outputFilePrefix);
	}

	/**
	 * For the given RPPA data, generates a ChiBE SIF graph.
	 *
	 * @param rows The proteomics data rows that are read from an external source
	 * @param valueThreshold The value threshold to be considered as significant
	 * @param graphType Either "compatible" or "conflicting"
	 * @param siteMatchStrict option to enforce matching a phosphorylation site in the network with
	 *                       the annotation of antibody
	 * @param geneCentric Option to produce a gene-centric or an antibody-centric graph
	 * @param outputFilePrefix If the user provides xxx, then xxx.sif and xxx.format are generated
	 * @throws IOException
	 */
	public static void generateRPPAGraphs(Collection<ProteomicsFileRow> rows, double valueThreshold, String graphType,
		boolean siteMatchStrict, int siteMatchProximityThreshold, boolean geneCentric, boolean addInUnknownEffects,
		String outputFilePrefix) throws IOException
	{
		ProteomicsLoader loader = new ProteomicsLoader(rows);
		// Associate change detectors
		loader.associateChangeDetector(new ThresholdDetector(valueThreshold), data -> data instanceof ProteinData);
		loader.associateChangeDetector(new ThresholdDetector(0.1), data -> data instanceof ActivityData);

		// Load signed relations
		Set<Relation> relations = NetworkLoader.load();
		loader.decorateRelations(relations);

		// Prepare causality searcher
		CausalitySearcher cs = new CausalitySearcher();
		cs.setForceSiteMatching(siteMatchStrict);
		cs.setAddInUnknownSigns(addInUnknownEffects);
		if (graphType.toLowerCase().startsWith("conflict")) cs.setCausal(false);
		cs.setSiteProximityThreshold(siteMatchProximityThreshold);

		// Search causal or conflicting relations
		Set<RelationAndSelectedData> relDat =  cs.run(relations);

		GraphWriter writer = new GraphWriter(relDat);
		writer.setUseGeneBGForTotalProtein(true);

		// Generate output
		if (geneCentric) writer.writeGeneCentric(outputFilePrefix);
		else writer.writeDataCentric(outputFilePrefix);
	}

	// Test in class. Bad practice. Tsk tsk tsk
	public static void main(String[] args) throws IOException
	{
		generateRPPAGraphs("/home/ozgun/Documents/JQ1/abdata-chibe.txt", "ID1", "Symbols", "Sites",
			"Effect", "/home/ozgun/Documents/JQ1/ovcar4_dif_drug_sig.txt", "change", 0.001,
			"compatible", true, 0, 0, false, false, "/home/ozgun/Temp/temp", null);
	}
}
