package MMS.utils;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;

import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

//por alocação: Histograma (frag interna), Line Chart/Time Series(ocupação do heap), Pie Chart (páginas livres vs. usadas), Gantt Chart (latência de alocação)
public class PerformanceChartExporter {

    public static void exportBarChart(double seqTime, double parTime, String outputPath) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        //dataset.addValue(seqTime, "Tempo (ms)", "Sequencial");
        //dataset.addValue(parTime, "Tempo (ms)", "Paralelo");
        dataset.addValue(seqTime, "Sequencial", "");
        dataset.addValue(parTime, "Paralelo", "");

        JFreeChart barChart = ChartFactory.createBarChart(
                "Tempo Médio de Execução",
                "Modo de Execução",
                "Tempo (ms)",
                dataset
        );
        barChart.setBackgroundPaint(Color.BLACK);
        //barChart.removeLegend();

        CategoryPlot plot = barChart.getCategoryPlot();
        formatPlot(plot);

        BarRenderer barRenderer = (BarRenderer) plot.getRenderer();
        formatBarRenderer(barRenderer);

        formatLegend(barChart);

        formatTitlesAndAxes(barChart, plot);

        File outputFile = new File(outputPath);
        ChartUtils.saveChartAsPNG(outputFile, barChart, 800, 600);
    }


    public static void exportBoxPlot(double[] seqTimes, double[] parTimes, String outputPath) throws IOException {
        /*DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        dataset.add(toList(seqTimes), "Tempo de Execução", "Sequencial");
        dataset.add(toList(parTimes), "Tempo de Execução", "Paralelo");*/

        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        dataset.add(toList(seqTimes), "Sequencial", "");
        dataset.add(toList(parTimes), "Paralelo", "");

        JFreeChart boxAndWhiskerChart = ChartFactory.createBoxAndWhiskerChart(
                "Boxplot de Tempos de Execução",
                "Modo de Execução",
                "Tempo (ms)",
                dataset,
                true
        );
        boxAndWhiskerChart.setBackgroundPaint(Color.BLACK);

        CategoryPlot plot = (CategoryPlot) boxAndWhiskerChart.getPlot();
        formatPlot(plot);

        BoxAndWhiskerRenderer renderer = (BoxAndWhiskerRenderer) plot.getRenderer();
        formatBoxAndWhiskerRenderer(renderer);

        formatTitlesAndAxes(boxAndWhiskerChart, plot);

        formatLegend(boxAndWhiskerChart);

        File boxPlotFile = new File(outputPath);
        ChartUtils.saveChartAsPNG(boxPlotFile, boxAndWhiskerChart, 800, 600);
    }


    public static void exportExecutionTimesBarChart(double[] seqTimes, double[] parTimes, String outputPath) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (int i = 0; i < seqTimes.length; i++) {
            String execLabel = Integer.toString(i + 1);
            dataset.addValue(seqTimes[i], "Sequencial", execLabel);
            dataset.addValue(parTimes[i],   "Paralelo",   execLabel);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Tempos por Execução",
                "Execução",
                "Tempo (ms)",
                dataset
        );
        chart.setBackgroundPaint(Color.BLACK);

        CategoryPlot plot = chart.getCategoryPlot();
        formatPlot(plot);

        formatTitlesAndAxes(chart, plot);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        formatBarRenderer(renderer);
        //renderer.setMaximumBarWidth(0.1);

        formatLegend(chart);

        ChartUtils.saveChartAsPNG(new File(outputPath), chart, 800, 600);
    }




    private static void formatTitlesAndAxes(JFreeChart chart, CategoryPlot plot) {
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 20));
        chart.getTitle().setPaint(Color.WHITE);

        //X
        plot.getDomainAxis().setTickLabelFont(new Font("SansSerif", Font.BOLD, 12));
        plot.getDomainAxis().setTickLabelPaint(Color.WHITE);
        plot.getDomainAxis().setLabelFont(new Font("SansSerif", Font.BOLD, 16));
        plot.getDomainAxis().setLabelPaint(Color.WHITE);

        //Y
        plot.getRangeAxis().setTickLabelFont(new Font("SansSerif", Font.BOLD, 12));
        plot.getRangeAxis().setTickLabelPaint(Color.WHITE);//valores
        plot.getRangeAxis().setLabelFont(new Font("SansSerif", Font.BOLD, 16));
        plot.getRangeAxis().setLabelPaint(Color.WHITE);//nomes
    }

    private static void formatLegend(JFreeChart chart) {
        LegendTitle legend = chart.getLegend();
        legend.setPosition(RectangleEdge.BOTTOM);
        legend.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        //legend.setVerticalAlignment(VerticalAlignment.BOTTOM);
        legend.setItemFont(new Font("SansSerif", Font.PLAIN, 14));
        legend.setBackgroundPaint(Color.BLACK);
        legend.setItemPaint(Color.WHITE);
        legend.setFrame(BlockBorder.NONE);
    }

    private static void formatPlot(CategoryPlot plot) {
        plot.setBackgroundPaint(Color.GRAY);
        plot.setRangeGridlinePaint(Color.WHITE);
        plot.getDomainAxis().setLowerMargin(0.05);//
        plot.getDomainAxis().setUpperMargin(0.05);
        //plot.getDomainAxis().setCategoryMargin(0.1);
        //setItemMargin(double) para mesma categoria(series)
        plot.setOutlineVisible(false);
    }

    private static void formatBarRenderer(BarRenderer renderer) {
        renderer.setLegendShape(0, new Rectangle(10, 10));
        renderer.setLegendShape(1, new Rectangle(10, 10));
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesPaint(1, Color.BLUE);

        renderer.setDrawBarOutline(true);
        renderer.setMaximumBarWidth(0.2);
        renderer.setShadowVisible(true);

        renderer.setDefaultItemLabelsVisible(true);//mostrar label
        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.BOLD, 12));
        renderer.setDefaultItemLabelPaint(Color.GREEN);

        renderer.setDefaultOutlinePaint(Color.BLACK);//contorno da caixa
    }

    private static void formatBoxAndWhiskerRenderer(BoxAndWhiskerRenderer renderer) {
        renderer.setLegendShape(0, new Rectangle(10, 10));
        renderer.setLegendShape(1, new Rectangle(10, 10));
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesPaint(1, Color.BLUE);

        renderer.setMaximumBarWidth(0.2);
        renderer.setDefaultItemLabelsVisible(true);//mostrar label
        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.BOLD, 12));
        renderer.setDefaultItemLabelPaint(Color.GREEN);

        renderer.setDefaultOutlinePaint(Color.BLACK);//contorno da caixa
        renderer.setMeanVisible(true);
        renderer.setUseOutlinePaintForWhiskers(true);
        renderer.setMedianVisible(true);//linha mediana
    }


    private static List<Double> toList(double[] array) {
        return Arrays.stream(array).boxed().collect(Collectors.toList());
    }

}