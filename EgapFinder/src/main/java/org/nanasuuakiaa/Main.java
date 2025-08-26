//special thanks to DylanDC14 for the guidance and help!

package org.nanasuuakiaa;

import java.awt.*;
import java.util.List;
import java.util.stream.LongStream;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.ChestContent;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.structure.RuinedPortal;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;

public class Main extends JFrame {

    public static final MCVersion VERSION = MCVersion.v1_16_5;
    static RuinedPortal portall = new RuinedPortal(Dimension.OVERWORLD, VERSION);

    private JTable resultsTable;
    private DefaultTableModel tableModel;

    private volatile int maxThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
    private volatile int minEgaps = 3;
    private volatile long seedStart = 2000;
    private volatile long seedEnd = 3000;

    public Main() {
        setupGUI();
        startSeedSearch();
    }

    private void setupGUI() {
        setTitle("Egap Finder by NaNasuuAkiaa (t.me/NaNasuuAkiaa, disc @nanasuuakiaa, github.com/NaNasuuAkiaa)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(Color.WHITE);
        getContentPane().setBackground(Color.WHITE);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JTextField threadsField = new JTextField(String.valueOf(maxThreads), 4);
        JTextField egapsField = new JTextField(String.valueOf(minEgaps), 4);
        JTextField startField = new JTextField(String.valueOf(seedStart), 6);
        JTextField endField = new JTextField(String.valueOf(seedEnd), 6);
        JButton applyButton = new JButton("Apply");

        toolBar.add(new JLabel("Threads: "));
        toolBar.add(threadsField);
        toolBar.add(new JLabel(" Egaps: "));
        toolBar.add(egapsField);
        toolBar.add(new JLabel(" Starting range: "));
        toolBar.add(startField);
        toolBar.add(new JLabel(" Ending range: "));
        toolBar.add(endField);
        toolBar.add(applyButton);

        add(toolBar, BorderLayout.NORTH);

        applyButton.addActionListener(e -> {
            try {
                maxThreads = Integer.parseInt(threadsField.getText());
                minEgaps = Integer.parseInt(egapsField.getText());
                seedStart = Long.parseLong(startField.getText());
                seedEnd = Long.parseLong(endField.getText());

                JOptionPane.showMessageDialog(this,
                        "Updated:\n" +
                                "Threads=" + maxThreads + ", Egaps=" + minEgaps +
                                ", Range=" + seedStart + "-" + seedEnd);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error in parameters, please try again..");
            }
        });

        String[] columns = {"Seed", "X", "Z", "Distance", "Egaps", "Teleport Command"};
        tableModel = new DefaultTableModel(columns, 0);
        resultsTable = new JTable(tableModel);
        resultsTable.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.getViewport().setBackground(Color.WHITE);

        add(scrollPane, BorderLayout.CENTER);

        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private void startSeedSearch() {
        new Thread(() -> {
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",
                    String.valueOf(maxThreads));

            long BLOCK = 10000000L;

            while (true) {
                LongStream.range(seedStart, seedEnd).parallel().forEach(i -> {
                    ChunkRand rand = new ChunkRand();
                    for (long seed = i * BLOCK; seed < (i + 1) * BLOCK; seed++) {
                        checkStructureSeed(seed, rand);
                    }
                });
            }
        }).start();
    }

    private void checkStructureSeed(long seed, ChunkRand rand) {
        OverworldBiomeSource biomeSource = new OverworldBiomeSource(VERSION, seed);
        OverworldTerrainGenerator terrainGen = new OverworldTerrainGenerator(biomeSource);

        for (int regionX = -1; regionX <= 1; regionX++) {
            for (int regionZ = -1; regionZ <= 1; regionZ++) {

                CPos portalPos = portall.getInRegion(seed, regionX, regionZ, rand);
                if (portalPos == null) continue;

                int blockX = portalPos.getX() * 16 + 8;
                int blockZ = portalPos.getZ() * 16 + 8;

                double distanceFromSpawn = Math.sqrt(blockX * blockX + blockZ * blockZ);
                if (distanceFromSpawn > 1000) continue;

                if (!portall.canSpawn(portalPos, biomeSource)) continue;

                RuinedPortalGenerator portalGen = new RuinedPortalGenerator(VERSION);
                if (!portalGen.generate(terrainGen, portalPos, rand)) continue;

                List<ChestContent> chests = portall.getLoot(seed, portalGen, false);
                if (chests == null || chests.isEmpty()) continue;

                for (ChestContent chest : chests) {
                    if (chest == null || chest.getItems() == null) continue;

                    int chestEgaps = chest.getItems().stream()
                            .filter(item -> item != null && item.getItem() != null)
                            .filter(item -> "enchanted_golden_apple".equals(item.getItem().getName()) ||
                                    "Enchanted golden apple".equals(item.getItem().getName()))
                            .mapToInt(ItemStack::getCount).sum();

                    if (chestEgaps >= minEgaps) {
                        SwingUtilities.invokeLater(() -> {
                            Object[] row = {
                                    seed,
                                    blockX,
                                    blockZ,
                                    String.format("%.0f", distanceFromSpawn),
                                    chestEgaps,
                                    "/tp " + blockX + " ~ " + blockZ
                            };
                            tableModel.addRow(row);
                        });
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}
