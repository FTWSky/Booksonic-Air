
package org.airsonic.player.service.search;

import com.google.common.base.Function;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.DaoHelper;
import org.airsonic.player.dao.MusicFolderDao;
import org.airsonic.player.dao.MusicFolderTestData;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.HomeRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import static org.springframework.util.ObjectUtils.isEmpty;

@ContextConfiguration(
        locations = {
                "/applicationContext-service.xml",
                "/applicationContext-cache.xml",
                "/applicationContext-testdb.xml",
                "/applicationContext-mockSonos.xml" })
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
/*
 * Test cases related to #1142.
 * The filter is not properly applied when analyzing the query,
 * 
 * In the process of hardening the Analyzer implementation,
 * this problem is solved side by side.
 */
public class SearchServiceStartWithStopwardsTestCase {

    @ClassRule
    public static final SpringClassRule classRule = new SpringClassRule() {
        HomeRule homeRule = new HomeRule();

        @Override
        public Statement apply(Statement base, Description description) {
            Statement spring = super.apply(base, description);
            return homeRule.apply(spring, description);
        }
    };

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MusicFolderDao musicFolderDao;

    @Autowired
    private DaoHelper daoHelper;

    @Autowired
    private SearchService searchService;

    @Autowired
    private SettingsService settingsService;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Autowired
    ResourceLoader resourceLoader;

    @Before
    public void setup() throws Exception {
        populateDatabase();
    }

    private static boolean dataBasePopulated;

    private static Function<String, String> resolvePath = (childPath) ->{
        return MusicFolderTestData.resolveBaseMediaPath() + childPath;
    };
    
    private List<MusicFolder> musicFolders;
    
    private List<MusicFolder> getTestMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = new ArrayList<>();

            File musicDir = new File(resolvePath.apply("Search/StartWithStopwards"));
            musicFolders.add(new MusicFolder(1, musicDir, "accessible", true, new Date()));

        }
        return musicFolders;
    }

    private synchronized void populateDatabase() {

        if (!dataBasePopulated) {
            getTestMusicFolders().forEach(musicFolderDao::createMusicFolder);
            settingsService.clearMusicFolderCache();
            TestCaseUtils.execScan(mediaScannerService);
            System.out.println("--- Report of records count per table ---");
            Map<String, Integer> records = TestCaseUtils.recordsInAllTables(daoHelper);
            records.keySet().stream().filter(s -> s.equals("MEDIA_FILE") // 20
                    | s.equals("ARTIST") // 5
                    | s.equals("MUSIC_FOLDER")// 3
                    | s.equals("ALBUM"))// 5
                    .forEach(tableName -> System.out
                            .println("\t" + tableName + " : " + records.get(tableName).toString()));
            System.out.println("--- *********************** ---");
            dataBasePopulated = true;
        }
    }

    @Test
    public void testStartWithStopwards() {

        List<MusicFolder> folders = getTestMusicFolders();

        final SearchCriteria criteria = new SearchCriteria();
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setOffset(0);

        criteria.setQuery("will");
        SearchResult result = searchService.search(criteria, folders, IndexType.ARTIST_ID3);
        Assert.assertEquals("Williams hit by \"will\" ", 1, result.getTotalHits());

        criteria.setQuery("the");
        result = searchService.search(criteria, folders, IndexType.SONG);
        Assert.assertEquals("Theater hit by \"the\" ", 1, result.getTotalHits());

    }

}
