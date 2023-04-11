package com.zuoye.trino.schema;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.zuoye.trino.export.TrinoExport;
import io.trino.jdbc.TrinoDriver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ZhangXueJun
 * @Date 2023年04月11日
 */
public class TrinoSchemaDrop {

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws Exception {
        long starTime = System.currentTimeMillis();
        List<String> schemas = Lists.newArrayList(args[0]);
        TrinoSchemaDrop schemaDrop = new TrinoSchemaDrop();

        List<String> schemaTables = Lists.newArrayList();
        CountDownLatch latch = new CountDownLatch(schemas.size());
        for (String schema : schemas) {
            threadPool.submit(() -> {
                try (Connection connection = schemaDrop.getConnection();
                     PreparedStatement statement = connection.prepareStatement("show tables from  " + "hive." + schema);
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String table = resultSet.getString(1);
                        if (schema.equals("default") || schema.equals("information_schema")) {
                            continue;
                        }
                        schemaTables.add("hive."+ schema + "." + table);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(1, TimeUnit.MINUTES);
        System.out.println("tables:" + schemaTables.size());

        CountDownLatch latch1 = new CountDownLatch(schemaTables.size());
        for (String schemaTable : schemaTables) {
            threadPool.submit(() -> {
                try (Connection connection = schemaDrop.getConnection();
                     Statement statement = connection.createStatement();
                     ) {
                    boolean resultSet = statement.execute("drop table  " + schemaTable);
                } catch (Exception e) {
                    e.printStackTrace();
                }  finally {
                    latch1.countDown();
                }
            });
        }

        latch1.await(10, TimeUnit.MINUTES);


        try (Connection connection = schemaDrop.getConnection();
             Statement statement = connection.createStatement();
             ) {
            boolean resultSet = statement.execute("drop schema hive." + args[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }  finally {
            latch1.countDown();
        }

        System.out.println("删除表完毕,耗时：" + (System.currentTimeMillis() - starTime) / 1000 + "秒！");
    }

    public Connection getConnection() throws Exception {

        Class.forName(TrinoDriver.class.getName());

        // properties
        String url = "jdbc:trino://localhost:64855/hive";
        Properties properties = new Properties();
        properties.setProperty("user", "ad_hoc");
        Connection connection = DriverManager.getConnection(url, properties);
        return connection;
    }
}
