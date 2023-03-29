package com.zuoye.trino.export;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
 * @Date 2023年03月29日
 */
public class TrinoExport {

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws Exception {
        List<String> schemas = Lists.newArrayList();

        TrinoExport export = new TrinoExport();
        try (Connection connection = export.getConnection();
        PreparedStatement statement = connection.prepareStatement("show schemas ");
            ResultSet resultSet = statement.executeQuery();) {
            while (resultSet.next()) {
                String schema = resultSet.getString(1);
                if (schema.equals("default") || schema.equals("information_schema")) {
                    continue;
                }
                schemas.add(schema);
            }
            System.out.println("schema lists:" + schemas);
        }


        Map<String, List<String>> schemaTables = Maps.newHashMap();
        CountDownLatch latch = new CountDownLatch(schemas.size());
        for (String schema : schemas) {
            threadPool.submit(() -> {
                try (Connection connection = export.getConnection();
                     PreparedStatement statement = connection.prepareStatement("show tables from  " + "hive." + schema);
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String table = resultSet.getString(1);
                        if (schema.equals("default") || schema.equals("information_schema")) {
                            continue;
                        }
                        schemaTables.computeIfAbsent(schema,key -> new ArrayList<String>()).add(table);
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

        Map<String, File> files = Maps.newHashMap();
        for (Map.Entry<String, List<String>> mapEntry : schemaTables.entrySet()) {
            File file = new File(mapEntry.getKey());
            if (file.exists()) {
                file.delete();
            }

            if (!file.exists()) {
                file.createNewFile();
            }
            files.put(mapEntry.getKey(), file);

            List<String> tables = mapEntry.getValue();

            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));


            AtomicInteger count = new AtomicInteger();
            CountDownLatch tableLatch = new CountDownLatch(tables.size());
            for (String table : tables) {
                String fullTableName = "hive." + mapEntry.getKey() + "." + table;
                threadPool.submit(() -> {
                    try (Connection connection = export.getConnection();
                         PreparedStatement statement = connection.prepareStatement("show create table " + fullTableName);
                         ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            synchronized (fileWriter) {
                                fileWriter.newLine();
                                fileWriter.newLine();
                                fileWriter.append("drop table " + fullTableName + ";");

                                fileWriter.newLine();
                                fileWriter.append(resultSet.getString(1) + ";");
                                count.addAndGet(1);
                                if (count.get() % 100 == 0) {
                                    System.out.println("完成100张表写入:" + mapEntry.getKey());
                                    fileWriter.flush();
                                    count.set(0);
                                }
                            }
                        }
                    } catch (Exception e) {
//                        e.printStackTrace();
                        System.out.println("show create table " + fullTableName);
                    } finally {
                        tableLatch.countDown();
                    }
                });
            }
            tableLatch.await(10, TimeUnit.MINUTES);
            System.out.println("完成schema文件写入：" + mapEntry.getKey());
            fileWriter.flush();
            fileWriter.close();
        }

        System.out.println("写入文件完毕");
    }

    public Connection getConnection() throws Exception {

        Class.forName(TrinoDriver.class.getName());

        // properties
        String url = "jdbc:trino://localhost:64319/hive";
        Properties properties = new Properties();
        properties.setProperty("user", "ad_hoc");
        Connection connection = DriverManager.getConnection(url, properties);
        return connection;
    }
}
