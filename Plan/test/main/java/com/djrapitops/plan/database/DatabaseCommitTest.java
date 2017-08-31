package main.java.com.djrapitops.plan.database;

import main.java.com.djrapitops.plan.Plan;
import main.java.com.djrapitops.plan.data.TPS;
import main.java.com.djrapitops.plan.data.WebUser;
import main.java.com.djrapitops.plan.database.databases.SQLDB;
import main.java.com.djrapitops.plan.database.databases.SQLiteDB;
import main.java.com.djrapitops.plan.utilities.MiscUtils;
import main.java.com.djrapitops.plan.utilities.file.FileUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import test.java.utils.RandomData;
import test.java.utils.TestInit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JavaPlugin.class)
public class DatabaseCommitTest {

    private Plan plan;
    private SQLDB db;
    private int rows;

    @Before
    public void setUp() throws Exception {
        TestInit t = TestInit.init();
        plan = t.getPlanMock();

        db = new SQLiteDB(plan, "debug" + MiscUtils.getTime());
        db.init();

        File file = new File(plan.getDataFolder(), "Errors.txt");
        rows = FileUtil.lines(file).size();
    }

    @After
    public void tearDown() throws IOException, SQLException {
        db.close();

        File f = new File(plan.getDataFolder(), "Errors.txt");
        List<String> lines = FileUtil.lines(f);
        int rowsAgain = lines.size();
        if (rowsAgain > 0) {
            for (String line : lines) {
                System.out.println(line);
            }
        }

        assertTrue("Errors were caught.", rows == rowsAgain);
    }

    @Test
    public void testNoExceptionWhenCommitEmpty() throws Exception {
        db.init();

        db.commit(db.getConnection());
        db.commit(db.getConnection());
        db.commit(db.getConnection());
    }

    @Ignore("//TODO")
    @Test
    public void testCommitToDBFile() throws Exception {
        db.init();
    }

    @Ignore("//TODO")
    @Test
    public void testCommitToDBFile2() throws Exception {
        db.init();
        List<TPS> tps = RandomData.randomTPS();
//        db.getTpsTable().saveTPSData(tps);
        db.close();
        db.init();
        assertFalse(db.getTpsTable().getTPSData().isEmpty());
    }

    // TODO Commit tests for new Login save features.

    @Test
    public void testCommitToDBFile5() throws Exception {
        db.init();
        WebUser webUser = new WebUser("Test", "SHA1:rioegnorgiengoieng:oiegnoeigneo:352", 0);
        db.getSecurityTable().addNewUser(webUser);
        db.close();
        db.init();
        assertEquals(webUser, db.getSecurityTable().getWebUser("Test"));
    }
}