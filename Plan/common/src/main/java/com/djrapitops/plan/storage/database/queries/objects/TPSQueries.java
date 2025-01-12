/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.storage.database.queries.objects;

import com.djrapitops.plan.delivery.domain.DateObj;
import com.djrapitops.plan.gathering.domain.TPS;
import com.djrapitops.plan.gathering.domain.builders.TPSBuilder;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.building.Select;
import com.djrapitops.plan.storage.database.sql.tables.ServerTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;
import static com.djrapitops.plan.storage.database.sql.tables.TPSTable.*;

/**
 * Queries for {@link com.djrapitops.plan.gathering.domain.TPS} objects.
 *
 * @author Rsl1122
 */
public class TPSQueries {

    private TPSQueries() {
        /* Static method class */
    }

    public static Query<List<TPS>> fetchTPSDataOfServer(UUID serverUUID) {
        String sql = Select.all(TABLE_NAME)
                .where(SERVER_ID + "=" + ServerTable.STATEMENT_SELECT_SERVER_ID)
                .toString();

        return new QueryStatement<List<TPS>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public List<TPS> processResults(ResultSet set) throws SQLException {
                List<TPS> data = new ArrayList<>();
                while (set.next()) {

                    com.djrapitops.plan.gathering.domain.TPS tps = extractTPS(set);

                    data.add(tps);
                }
                return data;
            }
        };
    }

    public static TPS extractTPS(ResultSet set) throws SQLException {
        return TPSBuilder.get()
                .date(set.getLong(DATE))
                .tps(set.getDouble(TPS))
                .playersOnline(set.getInt(PLAYERS_ONLINE))
                .usedCPU(set.getDouble(CPU_USAGE))
                .usedMemory(set.getLong(RAM_USAGE))
                .entities(set.getInt(ENTITIES))
                .chunksLoaded(set.getInt(CHUNKS))
                .freeDiskSpace(set.getLong(FREE_DISK))
                .toTPS();
    }

    public static Query<List<TPS>> fetchTPSDataOfServer(long after, long before, UUID serverUUID) {
        String sql = Select.all(TABLE_NAME)
                .where(SERVER_ID + "=" + ServerTable.STATEMENT_SELECT_SERVER_ID)
                .and(DATE + ">=?").and(DATE + "<=?")
                .toString();

        return new QueryStatement<List<TPS>>(sql, 50000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, after);
                statement.setLong(3, before);
            }

            @Override
            public List<TPS> processResults(ResultSet set) throws SQLException {
                List<TPS> data = new ArrayList<>();
                while (set.next()) {
                    TPS tps = extractTPS(set);
                    data.add(tps);
                }
                return data;
            }
        };
    }

    public static Query<List<DateObj<Integer>>> fetchPlayersOnlineOfServer(long after, long before, UUID serverUUID) {
        String sql = SELECT + ServerTable.SERVER_UUID + ',' + DATE + ',' + PLAYERS_ONLINE +
                FROM + TABLE_NAME +
                INNER_JOIN + ServerTable.TABLE_NAME + " on " + ServerTable.TABLE_NAME + '.' + ServerTable.SERVER_ID + '=' + SERVER_ID +
                WHERE + ServerTable.SERVER_UUID + "=?" +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<List<DateObj<Integer>>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public List<DateObj<Integer>> processResults(ResultSet set) throws SQLException {
                List<DateObj<Integer>> ofServer = new ArrayList<>();

                while (set.next()) {
                    ofServer.add(new DateObj<>(set.getLong(DATE), set.getInt(PLAYERS_ONLINE)));
                }

                return ofServer;
            }
        };
    }

    public static Query<Map<UUID, List<TPS>>> fetchTPSDataOfAllServersBut(long after, long before, UUID leaveOut) {
        String sql = SELECT + '*' +
                FROM + TABLE_NAME +
                INNER_JOIN + ServerTable.TABLE_NAME + " on " + ServerTable.TABLE_NAME + '.' + ServerTable.SERVER_ID + '=' + SERVER_ID +
                WHERE + ServerTable.SERVER_UUID + "!=?" +
                AND + ServerTable.INSTALLED + "=?" +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Map<UUID, List<TPS>>>(sql, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, leaveOut.toString());
                statement.setBoolean(2, true);
                statement.setLong(3, before);
                statement.setLong(4, after);
            }

            @Override
            public Map<UUID, List<TPS>> processResults(ResultSet set) throws SQLException {
                Map<UUID, List<TPS>> byServer = new HashMap<>();
                while (set.next()) {
                    UUID serverUUID = UUID.fromString(set.getString(ServerTable.SERVER_UUID));
                    List<TPS> ofServer = byServer.getOrDefault(serverUUID, new ArrayList<>());

                    ofServer.add(extractTPS(set));

                    byServer.put(serverUUID, ofServer);
                }
                return byServer;
            }
        };
    }

    public static Query<Optional<DateObj<Integer>>> fetchPeakPlayerCount(UUID serverUUID, long afterDate) {
        String subQuery = '(' + SELECT + "MAX(" + PLAYERS_ONLINE + ')' + FROM + TABLE_NAME + WHERE + SERVER_ID + "=" + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + ">= ?)";
        String sql = SELECT +
                DATE + ',' + PLAYERS_ONLINE +
                FROM + TABLE_NAME +
                WHERE + SERVER_ID + "=" + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + ">= ?" +
                AND + PLAYERS_ONLINE + "=" + subQuery +
                ORDER_BY + DATE + " DESC LIMIT 1";

        return new QueryStatement<Optional<DateObj<Integer>>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, afterDate);
                statement.setString(3, serverUUID.toString());
                statement.setLong(4, afterDate);
            }

            @Override
            public Optional<DateObj<Integer>> processResults(ResultSet set) throws SQLException {
                if (set.next()) {
                    return Optional.of(new DateObj<>(
                            set.getLong(DATE),
                            set.getInt(PLAYERS_ONLINE)
                    ));
                }
                return Optional.empty();
            }
        };
    }

    public static Query<Optional<DateObj<Integer>>> fetchAllTimePeakPlayerCount(UUID serverUUID) {
        return fetchPeakPlayerCount(serverUUID, 0);
    }

    public static Query<Optional<TPS>> fetchLatestTPSEntryForServer(UUID serverUUID) {
        String sql = SELECT + "*" +
                FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                ORDER_BY + DATE + " DESC LIMIT 1";

        return new QueryStatement<Optional<TPS>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
            }

            @Override
            public Optional<TPS> processResults(ResultSet set) throws SQLException {
                if (set.next()) {
                    return Optional.of(TPSBuilder.get()
                            .date(set.getLong(DATE))
                            .tps(set.getDouble(TPS))
                            .playersOnline(set.getInt(PLAYERS_ONLINE))
                            .usedCPU(set.getDouble(CPU_USAGE))
                            .usedMemory(set.getLong(RAM_USAGE))
                            .entities(set.getInt(ENTITIES))
                            .chunksLoaded(set.getInt(CHUNKS))
                            .freeDiskSpace(set.getLong(FREE_DISK))
                            .toTPS());
                }
                return Optional.empty();
            }
        };
    }

    public static Query<Double> averageTPS(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + TPS + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Double>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Double processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getDouble("average") : -1.0;
            }
        };
    }

    public static Query<Double> averageCPU(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + CPU_USAGE + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Double>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Double processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getDouble("average") : -1.0;
            }
        };
    }

    public static Query<Long> averageRAM(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + RAM_USAGE + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? (long) set.getDouble("average") : -1L;
            }
        };
    }

    public static Query<Long> averageChunks(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + CHUNKS + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? (long) set.getDouble("average") : -1L;
            }
        };
    }

    public static Query<Long> averageEntities(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + ENTITIES + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? (long) set.getDouble("average") : -1L;
            }
        };
    }

    public static Query<Long> maxFreeDisk(long after, long before, UUID serverUUID) {
        String sql = SELECT + "MAX(" + FREE_DISK + ") as free" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("free") : -1L;
            }
        };
    }

    public static Query<Long> minFreeDisk(long after, long before, UUID serverUUID) {
        String sql = SELECT + "MIN(" + FREE_DISK + ") as free" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? set.getLong("free") : -1L;
            }
        };
    }

    public static Query<Long> averageFreeDisk(long after, long before, UUID serverUUID) {
        String sql = SELECT + "AVG(" + FREE_DISK + ") as average" + FROM + TABLE_NAME +
                WHERE + SERVER_ID + '=' + ServerTable.STATEMENT_SELECT_SERVER_ID +
                AND + DATE + "<?" +
                AND + DATE + ">?";
        return new QueryStatement<Long>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, serverUUID.toString());
                statement.setLong(2, before);
                statement.setLong(3, after);
            }

            @Override
            public Long processResults(ResultSet set) throws SQLException {
                return set.next() ? (long) set.getDouble("average") : -1L;
            }
        };
    }
}