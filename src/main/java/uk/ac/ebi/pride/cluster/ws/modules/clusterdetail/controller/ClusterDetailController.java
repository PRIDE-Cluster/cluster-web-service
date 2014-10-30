package uk.ac.ebi.pride.cluster.ws.modules.clusterdetail.controller;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.pride.cluster.ws.modules.clusterdetail.model.ClusterDetail;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 *
 */
@Api(value = "clusterDetail", description = "retrieve detailed information about clusters", position = 0)
@Controller
@RequestMapping(value = "/clusterDetail")
public class ClusterDetailController {

    private static final Logger logger = LoggerFactory.getLogger(ClusterDetailController.class);


    @ApiOperation(value = "retrieves cluster detail information by cluster ID", position = 1, notes = "retrieve a record of a specific cluster detail")
    @RequestMapping(value = "/{clusterId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public
    @ResponseBody
    ClusterDetail getClusterDetail(
            @ApiParam(value = "a cluster ID")
            @PathVariable("clusterId") int clusterId) {
        logger.info("Cluster " + clusterId + " detail requested");

        ClusterDetail res = new ClusterDetail();
        res.setId(clusterId);

        // TODO

        return res;
    }
}