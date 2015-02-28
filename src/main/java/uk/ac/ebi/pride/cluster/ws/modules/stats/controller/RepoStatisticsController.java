package uk.ac.ebi.pride.cluster.ws.modules.stats.controller;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import uk.ac.ebi.pride.cluster.ws.modules.stats.model.RepoStatistic;
import uk.ac.ebi.pride.cluster.ws.modules.stats.util.RepoStatsToWsStatsMapper;
import uk.ac.ebi.pride.spectracluster.repo.dao.IClusterRepoStatisticsReadDao;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusterRepoStatistics;

import java.util.List;

/**
 * Controller for accessing the statistics
 *
 * @author Rui Wang
 * @version $Id$
 */
@Api(value = "stats", description = "retrieve statistics about the repository", position = 0)
@Controller
@RequestMapping(value = "/stats")
public class RepoStatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(RepoStatisticsController.class);

    @Autowired
    IClusterRepoStatisticsReadDao clusterRepoStatisticsReadDao;

    @ApiOperation(value = "returns the general statistics for the entire repository", position = 1, notes = "retrieve general statistics")
    @RequestMapping(value = "/general", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK) // 200
    public @ResponseBody List<RepoStatistic> getClusterRepoStatistics() {
        List<ClusterRepoStatistics> generalStatistics = clusterRepoStatisticsReadDao.getGeneralStatistics();
        return RepoStatsToWsStatsMapper.asStatsList(generalStatistics);
    }
}