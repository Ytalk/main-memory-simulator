package MMS;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.io.IOException;

public class PerformanceChartGenerator {

    public static void exportComparisonChart(double seqTime, double parTime, String outputPath) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(seqTime, "Tempo (ms)", "Sequencial");
        dataset.addValue(parTime, "Tempo (ms)", "Paralelo");

        JFreeChart chart = ChartFactory.createBarChart(
                "Desempenho: Sequencial VS Paralelo",
                "Modo de Execução",
                "Tempo (ms)",
                dataset
        );

        File outputFile = new File(outputPath);
        ChartUtils.saveChartAsPNG(outputFile, chart, 800, 600);
        System.out.println("Gráfico exportado para: " + outputPath);
    }

}
