package uk.ac.ebi.pride.cluster.ws.modules.cluster.controller;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.pride.cluster.search.model.SolrCluster;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.ws.error.exception.ResourceNotFoundException;
import uk.ac.ebi.pride.cluster.ws.modules.cluster.filter.*;
import uk.ac.ebi.pride.cluster.ws.modules.cluster.model.*;
import uk.ac.ebi.pride.cluster.ws.modules.cluster.util.*;
import uk.ac.ebi.pride.cluster.ws.modules.spectrum.model.Spectrum;
import uk.ac.ebi.pride.indexutils.results.PageWrapper;
import uk.ac.ebi.pride.spectracluster.repo.dao.cluster.IClusterReadDao;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusterDetail;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusterSummary;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusteredPSMDetail;
import uk.ac.ebi.pride.spectracluster.repo.utils.ModificationDetailFetcher;

import java.util.*;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 */
@Api(value = "cluster", description = "retrieve information about clusters", position = 0)
@Controller
@RequestMapping(value = "/cluster")
public class ClusterController {

    private static final Logger logger = LoggerFactory.getLogger(ClusterController.class);

    public static final float DEFAULT_MINIMUM_PSM_RANKING = 1.1f;

    public static final String NO_MODIFICATION = "NONE";

    @Autowired
    IClusterReadDao clusterReaderDao;

    @Autowired
    IClusterSearchService clusterSearchService;

    @Autowired
    ModificationDetailFetcher modificationDetailFetcher;

    @ApiOperation(value = "Endpoint that lists clusters for given search criteria", position = 1, notes = "search functionality")
    @RequestMapping(value = "/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusterSearchResults simpleSearchClusters(
            @ApiParam(value = "general search term against multiple fields including: peptide sequence")
            @RequestParam(value = "q", required = false, defaultValue = "") String q,
            @ApiParam(value = "filter cluster by Max Ratio Peptide Sequence")
            @RequestParam(value = "peptide", required = false, defaultValue = "") Set<String> peptides,
            @ApiParam(value = "filter clusters by modification name")
            @RequestParam(value = "modFilters", required = false) Set<String> modFilters,
            @ApiParam(value = "filter clusters by species name")
            @RequestParam(value = "speciesFilters", required = false) Set<String> speciesFilters,
            @ApiParam(value = "the field to sort on (e.g. num_spectra, num_projects, or max_ratio)")
            @RequestParam(value = "sort", required = false) String fieldToSort,
            @ApiParam(value = "the direction of the sort (either desc or asc)")
            @RequestParam(value = "order", required = false, defaultValue = "desc") String order,
            @ApiParam(value = "allow faceted results")
            @RequestParam(value = "facets", required = false, defaultValue = "false") Boolean facets,
            @ApiParam(value = "allow highlights results")
            @RequestParam(value = "highligts", required = false, defaultValue = "false") Boolean highlights,
            @ApiParam(value = "0-based page number")
            @RequestParam(value = "page", required = true, defaultValue = "0") int page,
            @ApiParam(value = "maximum number of results per page")
            @RequestParam(value = "size", required = true, defaultValue = "20") int size
    ) {

        logger.info("Fetched clusters for" +
                        " query: " + q +
                        " peptide: " + peptides +
                        " modFilters: " + modFilters +
                        " speciesFilters: " + speciesFilters +
                        " sort: " + fieldToSort +
                        " order: " + order +
                        " facets: " + facets +
                        " highlights: " + highlights +
                        " page: " + page +
                        " size: " + size
        );

        Sort sort = generateSort(fieldToSort, order);

        PageWrapper<SolrCluster> res =
                clusterSearchService.findClusterByQuery(
                        q,
                        peptides,
                        modFilters,
                        speciesFilters,
                        sort,
                        new PageRequest(page, size));


        ClusterSearchResults results = new ClusterSearchResults();
        results.setPageNumber(page);
        results.setPageSize(size);
        results.setTotalResults(res.getPage().getTotalElements());
        logger.info("Total results is " + results.getTotalResults());
        results.setResults(SolrClusterToWsClusterMapper.asClusterList(res.getPage()));

        if (highlights) {
            for (Map.Entry<SolrCluster, Map<String, List<String>>> clusterMapEntry : res.getHighlights().entrySet()) {
                results.addHighlight(clusterMapEntry.getKey().getId(), clusterMapEntry.getValue());
            }
        }

        if (facets) {
            logger.info("Before querying for facets");

            Map<String, Map<String, Long>> factes = clusterSearchService.findClusterFacetByQuery(
                    q,
                    peptides,
                    modFilters,
                    speciesFilters);
            results.setFacetsMap(factes);

            logger.info("After querying for facets");
        }
        return results;
    }

    /**
     * Generate a Sort for a given set of criterias
     *
     * @param fieldToSort field to be sorted
     * @param order       sort order
     * @return an sort object
     */
    private Sort generateSort(String fieldToSort, String order) {

        Sort sort = null;

        if (fieldToSort != null && !fieldToSort.trim().isEmpty()) {
            String clusterField = ClusterToClusterFieldMapper.mapToClusterField(fieldToSort);
            if (clusterField != null) {
                Sort.Direction direction = Sort.Direction.fromString(order);
                sort = new Sort(direction, clusterField);
            }
        }

        return sort;
    }

    @ApiOperation(value = "Endpoint that retrieves cluster information by cluster ID or cluster UUID", position = 2, notes = "retrieve a record of a specific cluster")
    @RequestMapping(value = "/{clusterId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    Cluster getClusterSummaryByID(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster summary " + clusterId + " requested");

        ClusterSummary clusterSummary = findClusterSummaryByID(clusterId);

        if (clusterSummary.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            List<ClusteredPSMDetail> clusteredPSMSummaries = clusterReaderDao.findClusteredPSMSummaryByClusterId(clusterSummary.getId(), DEFAULT_MINIMUM_PSM_RANKING);
            return RepoClusterToWsClusterMapper.asCluster(clusterSummary, clusteredPSMSummaries.get(0), modificationDetailFetcher);
        }
    }

    @ApiOperation(value = "Endpoint that retrieves cluster species information only", position = 4,
            notes = "retrieve a record of a specific cluster consensus spectrum")
    @RequestMapping(value = "/{clusterId}/species", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusterSpeciesCounts getClusterSpecies(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster " + clusterId + " species requested");

        // get the cluster
        ClusterDetail repoCluster = findClusterDetailByID(clusterId);

        if (repoCluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            return ClusterStatsCollector.collectClusterSpeciesCounts(repoCluster);
        }
    }


    @ApiOperation(value = "Endpoint that retrieves cluster modification information only", position = 5,
            notes = "retrieve modification records of a specific cluster")
    @RequestMapping(value = "/{clusterId}/modification", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusterModificationCounts getClusterPTMs(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster " + clusterId + " modifications requested");

        // get the cluster
        ClusterDetail repoCluster = findClusterDetailByID(clusterId);

        if (repoCluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            return ClusterStatsCollector.collectClusterModificationCounts(repoCluster);
        }
    }


    @ApiOperation(value = "Endpoint that retrieves cluster consensus spectrum information only", position = 6,
            notes = "retrieve a record of a specific cluster consensus spectrum")
    @RequestMapping(value = "/{clusterId}/consensusSpectrum", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    Spectrum getClusterConsensusSpectrum(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster " + clusterId + " consensus spectra requested");

        ClusterSummary repoCluster = findClusterSummaryByID(clusterId);

        if (repoCluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            return RepoClusterToWsClusterMapper.asConsensusSpectrum(repoCluster);
        }

    }

    @ApiOperation(value = "Endpoint that returns peptides for a given Cluster ID or UUID", position = 7, notes = "retrieve peptides for a given Cluster ID or UUID")
    @RequestMapping(value = "/{clusterId}/peptide", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusteredPeptideList getClusterPeptides(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster " + clusterId + " peptides requested");

        ClusterDetail cluster = findClusterDetailByID(clusterId);

        if (cluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            return ClusteredPeptideFinder.findClusteredPeptides(cluster);
        }
    }

    @ApiOperation(value = "Endpoint that returns PSMs for a given Cluster ID or UUID", position = 7, notes = "retrieve PSMs for a given Cluster ID or UUID")
    @RequestMapping(value = "/{clusterId}/psm", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusteredPSMList getClusterPSMs(
            @ApiParam(value = "a cluster ID or cluster UUID")
            @PathVariable("clusterId") String clusterId,
            @ApiParam(value = "peptide sequence")
            @RequestParam(value = "sequence", required = false) String sequence,
            @ApiParam(value = "modifications with their positions, or 'NONE' for no modifications")
            @RequestParam(value = "mod", required = false) String modification,
            @ApiParam(value = "project accession")
            @RequestParam(value = "project", required = false) String projectAccession,
            @ApiParam(value = "0-based page number")
            @RequestParam(value = "page", required = true, defaultValue = "0") int page,
            @ApiParam(value = "maximum number of results per page")
            @RequestParam(value = "size", required = true, defaultValue = "20") int size) {

        logger.info("Cluster " + clusterId + " PSMs requested for " +
                " peptide sequence: " + sequence +
                " modification: " + modification +
                " project: " + projectAccession +
                " page: " + page +
                " size: " + size);

        ClusterDetail cluster = findClusterDetailByID(clusterId);

        if (cluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            List<IPredicate<ClusteredPSMDetail>> psmFilters = new ArrayList<IPredicate<ClusteredPSMDetail>>();

            // sequence filter
            if (sequence != null && !"".equals(sequence)) {
                psmFilters.add(new ClusteredPSMSequenceFilter(sequence));
            }

            // modification filter
            if (modification != null && !"".equals(modification)) {
                if (modification.equalsIgnoreCase(NO_MODIFICATION)) {
                    psmFilters.add(new ClusteredPSMNoModificationFilter());
                } else {
                    psmFilters.add(new ClusteredPSMModificationFilter(modification));
                }
            }

            if (projectAccession != null && !"".equals(projectAccession)) {
                psmFilters.add(new ClusteredPSMProjectFilter(cluster, projectAccession));
            }

            return ClusteredPeptideFinder.findClusteredPSMs(cluster, Predicates.and(psmFilters), page, size);
        }
    }

    @ApiOperation(value = "Endpoint that returns projects for a given Cluster ID or UUID", position = 8, notes = "retrieve projects for a given Cluster ID or UUID")
    @RequestMapping(value = "/{clusterId}/project", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusteredProjectList getClusterProjects(
            @ApiParam(value = "a cluster ID or UUID")
            @PathVariable("clusterId") String clusterId,
            @ApiParam(value = "peptide sequence")
            @RequestParam(value = "sequence", required = false) String sequence,
            @ApiParam(value = "modifications with their positions, or 'NONE' for no modifications")
            @RequestParam(value = "mod", required = false) String modification) {
        logger.info("Cluster " + clusterId + " projects requested");

        ClusterDetail cluster = findClusterDetailByID(clusterId);

        if (cluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            List<IPredicate<ClusteredPSMDetail>> psmFilters = new ArrayList<IPredicate<ClusteredPSMDetail>>();

            // sequence filter
            if (sequence != null && !"".equals(sequence)) {
                psmFilters.add(new ClusteredPSMSequenceFilter(sequence));
            }

            // modification filter
            if (modification != null && !"".equals(modification)) {
                if (modification.equalsIgnoreCase(NO_MODIFICATION)) {
                    psmFilters.add(new ClusteredPSMNoModificationFilter());
                } else {
                    psmFilters.add(new ClusteredPSMModificationFilter(modification));
                }
            }

            return ClusteredProjectFinder.findClusteredProjects(cluster, Predicates.and(psmFilters));
        }
    }

    @ApiOperation(value = "Endpoint that returns delta m/z statistics for a given Cluster ID or UUID", position = 9,
            notes = "retrieve delta m/z statistics for a given Cluster ID or UUID")
    @RequestMapping(value = "/{clusterId}/deltaMz", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    PSMDeltaMZStatistics getClusterPsmDeltaMZStatistics(
            @ApiParam(value = "a cluster ID or UUID")
            @PathVariable("clusterId") String clusterId) {
        logger.info("Cluster " + clusterId + " delta m/z statistics requested");

        ClusterDetail cluster = findClusterDetailByID(clusterId);

        logger.debug("Retrieved cluster details for cluster: " + clusterId);

        if (cluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            PSMDeltaMZStatistics psmDeltaMZStatistics = ClusterStatsCollector.collectPSMDeltaMZStatistics(cluster);
            logger.debug("Collected PSM delta m/z statistics");
            return psmDeltaMZStatistics;
        }
    }


    @ApiOperation(value = "Endpoint that returns spectrum similarity statistics for a given Cluster ID or UUID", position = 10,
            notes = "retrieve spectrum similarity statistics for a given Cluster ID or UUID")
    @RequestMapping(value = "/{clusterId}/similarity", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    SpectrumSimilarityStatistics getClusterSpectrumSimilarityStatistics(
            @ApiParam(value = "a cluster ID")
            @PathVariable("clusterId") long clusterId) {
        logger.info("Cluster " + clusterId + " spectrum similarity statistics requested");

        ClusterDetail cluster = clusterReaderDao.findCluster(clusterId);

        logger.debug("Retrieved cluster details for cluster: " + clusterId);

        if (cluster.getId() == null) {
            throw new ResourceNotFoundException("Cluster using ID: " + clusterId);
        } else {
            SpectrumSimilarityStatistics spectrumSimilarityStatistics = ClusterStatsCollector.collectSpectrumSimilarityStatistics(cluster);
            logger.debug("Collected spectrum similarity statistics");
            return spectrumSimilarityStatistics;
        }
    }


    /**
     * Find cluster summary according the type of the ID
     *
     * @param id cluster id can be either the database id or UUID
     * @return cluster summary
     */
    private ClusterSummary findClusterSummaryByID(String id) {
        if (isLong(id)) {
            long clusterId = Long.parseLong(id);
            return clusterReaderDao.findClusterSummary(clusterId);
        } else if (isUUID(id)) {
            return clusterReaderDao.findClusterSummaryByUUID(id);
        } else {
            return new ClusterSummary();
        }
    }

    @Cacheable(value="clusters", key="#id", unless = "#result.totalNumberOfSpectra > 200")
    @CacheEvict(value = "clusters", condition = "")
    private ClusterDetail findClusterDetailByID(String id) {
        if (isLong(id)) {
            long clusterId = Long.parseLong(id);
            return clusterReaderDao.findCluster(clusterId);
        } else if (isUUID(id)) {
            return clusterReaderDao.findClusterByUUID(id);
        } else {
            return new ClusterDetail();
        }
    }

    private boolean isUUID(String id) {
        try {
            UUID.fromString(id);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isLong(String id) {
        try {
            Long.parseLong(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//    @ApiOperation(value = "list similar cluster summaries given a list of peaks", position = 6, notes = "additive clustering functionality")
//    @RequestMapping(value = "/nearest", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
//    @ResponseStatus(HttpStatus.OK) // 200
//    public
//    @ResponseBody
//    ClusterSearchResults getSimilarClusters(
//            @ApiParam(value = "precursor MZ")
//            @RequestParam(value = "precursor", required = false, defaultValue = "") String precursor,
//            @ApiParam(value = "peak list to compare to")
//            @RequestParam(value = "peaks", required = false, defaultValue = "") String peaks,
//            @ApiParam(value = "0-based page number")
//            @RequestParam(value = "page", required = true, defaultValue = "0") int page,
//            @ApiParam(value = "maximum number of results per page")
//            @RequestParam(value = "size", required = true, defaultValue = "10") int size
//
//    ) {
//
//        logger.info("Fetched clusters for ");
//        logger.info("page: " + page);
//        logger.info("size: " + size);
//        logger.info("precursor: " + precursor);
//        logger.info("peaks ");
//        logger.info(peaks);
//
//        QueryInputPeaks query = new QueryInputPeaks();
//        parsePeaks(peaks, query);
//        double precursorMz = Double.parseDouble(precursor);
//
//        double[] lowResMz = LowResUtils.toLowResByBucketMean(query.mzValues, 20);
//        double[] lowResIntensity = LowResUtils.toLowResByBucketMean(query.intensityValues, 20);
//        logDoubleArray("MZ", lowResMz);
//        logDoubleArray("Intensity", lowResIntensity);
//
//        Page<SolrCluster> clusters = clusterSearchService.findByNearestPeaks(
//                "HIGH",
//                precursorMz,
//                1.0,
//                lowResMz,
//                lowResIntensity,
//                new PageRequest(page, size)
//        );
//
//        ClusterSearchResults results = new ClusterSearchResults();
//        results.setPageNumber(page);
//        results.setPageSize(size);
//        results.setTotalResults(clusters.getTotalElements());
//        logger.info("Total results is " + clusters.getTotalElements());
//        results.setResults(SolrClusterToWsClusterMapper.asClusterList(clusters));
//
//        return results;
//
//    }
//
//    private void logDoubleArray(String tag, double[] values) {
//        for (double va : values) {
//            logger.info(tag + " is " + va);
//        }
//    }
//
//    private void parsePeaks(String peaks, QueryInputPeaks query) {
//        String[] peakStrings = peaks.split("[ \\n]+");
//        query.mzValues = new double[peakStrings.length / 2];
//        query.intensityValues = new double[peakStrings.length / 2];
//
//
//        int j = 0;
//        for (int i = 0; i < peakStrings.length; i = i + 2) {
//            query.mzValues[j] = Double.parseDouble(peakStrings[i]);
//            query.intensityValues[j] = Double.parseDouble(peakStrings[i + 1]);
//            j++;
//        }
//    }
//
//    private List<Cluster> getTestClusters(int n) {
//        List<Cluster> res = new LinkedList<Cluster>();
//
//        for (int i = 0; i < n; i++) {
//            Cluster cluster = new Cluster();
//            cluster.setId(i);
//            res.add(cluster);
//        }
//
//        return res;
//    }
}
