// What are autoencoders?
// auteencoders are neural networks used for efficient data codings.
// The aim of autoencoders is to learn representation or a encoding of a set of data
// Autoencoder can be used for data denoising, dimentionality reduction or for data compresion
// Features generated by autoencoder can then be fed into another algorithm
// used for classification, clustering or for anomly detection

// Autoencoder can also be used for data visualization of mulitdimentionel datasets
// that cannot be easiely plotted

// How does autoencoder work?
// Autoencoders contains of three parts - encoding function, decoding function and distance function
// An input is feed into encoding function and turned into compressed representation
// Then the decoder processes generated output
// and learns how to produce original input from the compressed representation
// distance function is used in order to correct the error produced by the decoder

// This is example is a neural network that is designed for anomaly detection inside MNIST dataset of handwritten digits
// Anomaly does not require labeled datasets
// The type of anomaly detector that we are building in this example
// takes use reconstruction error analysis.
// The theory is that datasets whith higher reconstruction error is somewhat different from rest of the dataset

// Use cases for this type of anomaly detection can be:
// Network Intrusions | Froud Detection | System Monitoring | Sensor Network Event Detection.


import org.nd4j.linalg.primitives.Pair;
import org.slf4j.Logger;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.learning.config.AdaGrad;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.nd4j.linalg.factory.Nd4j.argMax;

public class Main {

    private static int seed = 12345;
    private static int rows = 28;
    private static int columns = 28;
    private static int numOutputs = 10;
    private static int batchSize = 100;
    private static int nEpochs = 10;
    private static double trainPercent = 0.8;
    private static int numExamples = 50000;

    private static Logger log;

    public static void main(String[] args) throws IOException {
        log = LoggerFactory.getLogger(Main.class);

        MnistDataSetIterator iter = new MnistDataSetIterator(batchSize, numExamples, false);

        ArrayList<INDArray> featuresTrain = new ArrayList<>();
        ArrayList<INDArray> featuresTest = new ArrayList<>();
        ArrayList<INDArray> labelsTest = new ArrayList<>();

        Random rand = new Random(seed);
        while (iter.hasNext()) {
            DataSet next = iter.next();
            SplitTestAndTrain split = next.splitTestAndTrain((int) (trainPercent * 100), rand);

            featuresTrain.add(split.getTrain().getFeatures());

            DataSet dsTest = split.getTest();
            featuresTest.add(dsTest.getFeatures());
            INDArray indexes = argMax(dsTest.getLabels(), 1); // convert from one-hot representation to index

            labelsTest.add(indexes);
        }

        MultiLayerNetwork net = createNetwork();
        net.setListeners(new ScoreIterationListener(100));

        trainNetwork(net, featuresTrain);
        evaluateModel(net, featuresTest, labelsTest);
    }

    private static MultiLayerNetwork createNetwork() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(AdaGrad.builder()
                        .learningRate(0.05)
                        .build())
                .l2(0.0001)
                .weightInit(WeightInit.XAVIER)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(rows * columns)
                        .nOut(250)
                        .build())
                .layer(1, new DenseLayer.Builder()
                        .nIn(250)
                        .nOut(10)
                        .build())
                .layer(2, new DenseLayer.Builder()
                        .nIn(10)
                        .nOut(250)
                        .build())
                .layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(250)
                        .nOut(rows * columns)
                        .build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        return net;
    }

    private static void trainNetwork(MultiLayerNetwork network, ArrayList<INDArray> featuresTrain) {
        log.info("Model training: starts");
        for (int i = 0; i < nEpochs; i++) {
            log.info("Model training: beginning epoch number " + (i + 1));
            for (INDArray data : featuresTrain) {
                // Using the original in parameter as both input and output
                // because we are trying to are trying to generate original image from the encoded data
                network.fit(data, data);
            }

            log.info("Model training: completed epoch number " + (i + 1));
        }
        log.info("Model training: ends");
    }

    private static void evaluateModel(MultiLayerNetwork model, ArrayList<INDArray> featuresTest,
                                      ArrayList<INDArray> labelsTest) throws IOException {
        // Evaluate the model on the test data
        // Score each example in the test set separately
        // Compose a map that relates each digit to a list of (score, example) pairs
        // Then find N best and N worst scores per digit

        log.info("Evaluating model: starts");

        HashMap<Integer, List<Pair<Double, INDArray>>> listsByDigit = new HashMap<>();

        for (int number = 0; number < 10; number++) listsByDigit.put(number, new ArrayList<>());

        // The passed features are tensors of batches
        for (int i = 0; i < featuresTest.size(); i++) {
            // getting single batch
            INDArray testData = featuresTest.get(i);
            INDArray labels = labelsTest.get(i);

            // looping through each dataset in batch
            for (int ii = 0; ii < testData.rows(); ii++) {
                // Getting single featureset
                INDArray example = testData.getRow(ii);
                // And corresponding label
                int digit = (int) labels.getDouble(ii);
                // calculating loss of the encode-decode result
                // we're still using original dataset as label for loss calculation
                // even if only single dataset is passed we have to reshape the tensor to
                // to format of list of datasets
                double score = model.score(new DataSet(example.reshape(-1, rows * columns),
                        example.reshape(-1, rows * columns)));
                List<Pair<Double, INDArray>> digitPairs = listsByDigit.get(digit);
                digitPairs.add(new Pair<>(score, example));
            }
        }

        // Sort each list by score
        Comparator<Pair<Double, INDArray>> c = Comparator.comparingDouble(Pair::getLeft);

        // Sort results by score
        for (List<Pair<Double, INDArray>> list : listsByDigit.values()) list.sort(c);

        INDArray[] best = new INDArray[5];
        INDArray[] worst = new INDArray[5];

        // get every list in hashmap
        listsByDigit.forEach((key, value) -> {
            for (int i = 0; i < 5; i++) {
                best[i] = value.get(i).getRight();
                worst[i] = value.get(value.size() - i - 1).getRight();
            }
            try {
                visualiseData(best, worst, "results/" + key);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        log.info("Evaluating model: ends");

    }

    private static void visualiseData(INDArray[] best, INDArray[] worst, String folder) throws IOException {
        log.info("Visualising data: starts");

        for (int i = 0; i < best.length; i++) {
            File file = new File(folder + "/best/" + i + ".png");
            encodeArrayToImage(best[i], file);

            file = new File(folder + "/worst/" + i + ".png");
            encodeArrayToImage(worst[i], file);
        }

        log.info("Visualising data: ends");
    }

    private static void encodeArrayToImage(INDArray array, File file) throws IOException {
        BufferedImage bi = new BufferedImage(rows, columns, BufferedImage.TYPE_BYTE_GRAY);

        for (int i = 0; i < rows * columns; i++) {
            bi.getRaster().setSample(i % rows, i / columns, 0, (int) (255 * array.getDouble(i)));
        }

        ImageIO.write(bi, "PNG", file);
    }

}
