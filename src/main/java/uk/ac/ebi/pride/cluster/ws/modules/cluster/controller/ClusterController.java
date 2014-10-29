package uk.ac.ebi.pride.cluster.ws.modules.cluster.controller;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.pride.cluster.ws.modules.cluster.model.ClusterSummary;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 *
 */
@Api(value = "cluster", description = "retrieve information about clusters", position = 0)
@Controller
@RequestMapping(value = "/cluster")
public class ClusterController {

    private static final Logger logger = LoggerFactory.getLogger(ClusterController.class);


    @ApiOperation(value = "retrieve cluster information by ID", position = 1, notes = "retrieve a detailed record of a specific cluster")
    @RequestMapping(value = "/{clusterId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusterSummary getClusterDetail(
            @ApiParam(value = "a cluster ID")
            @PathVariable("clusterId") int clusterId) {
        logger.info("Cluster " + clusterId + " detail requested");

        ClusterSummary res = new ClusterSummary();
        res.setId(clusterId);

        // TODO

        return res;
    }

    @ApiOperation(value = "list clusters for given criteria", position = 2, notes = "search functionality")
    @RequestMapping(value = "/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    List<ClusterSummary> simpleSearchClusters(
            @ApiParam(value = "search term to query for")
            @RequestParam(value = "q", required = false, defaultValue = "") String term
    ) {

        logger.debug("Fetched clusters for term: " + term);

        return getTestClusterSummaries(10);
    }

    @ApiOperation(value = "list similar clusters given a list of peaks", position = 3, notes = "additive clustering functionality")
    @RequestMapping(value = "/similar", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    List<ClusterSummary> getSimilarClusters(
            @ApiParam(value = "peak list to compare to")
            @RequestParam(value = "peaks", required = false, defaultValue = "") String peaks
    ) {

        logger.debug("Fetched clusters for peak list: " + peaks);

        return getTestClusterSummaries(5);
    }


    private List<ClusterSummary> getTestClusterSummaries(int n) {
        List<ClusterSummary> res = new LinkedList<ClusterSummary>();

        for (int i=0;i<n;i++) {
            ClusterSummary clusterSummary = new ClusterSummary();
            clusterSummary.setId(i);
            res.add(clusterSummary);
        }

        return res;
    }
}
