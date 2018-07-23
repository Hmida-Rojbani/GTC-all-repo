package com.gtc.persistor.service;

import com.google.common.io.MoreFiles;
import com.gtc.model.provider.AggregatedOrder;
import com.gtc.model.provider.OrderBook;
import com.gtc.persistor.config.PersistConfig;
import com.newrelic.api.agent.Trace;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static com.gtc.persistor.config.Const.Persist.PERSIST_S;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Created by Valentyn Berezin on 01.07.18.
 */
@Service
@RequiredArgsConstructor
public class BookPersistor {

    private static final String TO_ZIP = ".to_zip";
    private static final String GZ = ".gz";
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
    private static final Pattern TIME_PATTERN = Pattern.compile(".+(\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d)\\.tsv");
    private AtomicReference<LocalDateTime> maxTime = new AtomicReference<>(LocalDateTime.MIN);

    private final PersistConfig cfg;
    private final OrderBookRepository bookRepository;

    @Trace(dispatcher = true)
    @Scheduled(fixedDelayString = PERSIST_S)
    public void persist() {
        List<OrderBook> orderBooks = new ArrayList<>(bookRepository.getOrders());
        bookRepository.clear();
        appendData(orderBooks);
    }

    @Trace(dispatcher = true)
    @Scheduled(fixedDelayString = PERSIST_S)
    public void zipIfNecessary() {
        zipFinishedDataIfNecessary();
    }

    private void appendData(List<OrderBook> books) {
        Map<String, List<OrderBook>> booksToFile = new HashMap<>();
        String suffix = getSuffixAndLockDate();

        books.forEach(it -> booksToFile
                .computeIfAbsent(baseName(it, suffix), id -> new ArrayList<>())
                .add(it)
        );

        booksToFile.forEach(this::writeBooks);
    }

    @SneakyThrows
    private void writeBooks(String filename, List<OrderBook> books) {
        if (books.isEmpty()) {
            return;
        }
        
        books.sort(Comparator.comparingLong(a -> a.getMeta().getTimestamp()));
        Path dest = Paths.get(cfg.getDir(), filename);
        boolean exists = dest.toFile().exists();

        try (Writer file = MoreFiles.asCharSink(dest, UTF_8, CREATE, APPEND).openBufferedStream()) {
            if (!exists) {
                writeHeader(file, books.get(0).getHistogramBuy().length, books.get(0).getHistogramSell().length);
            }

            books.forEach(book -> writeBook(file, book));
        }
    }

    @SneakyThrows
    private void writeHeader(Writer file, int histogramBuyPrecision, int histogramSellPrecision) {
        file.write("Time\t");
        file.write("Best buy\t");
        file.write("Best sell\t");
        file.write("Histogram price sell step\t");
        file.write("Histogram price buy step\t");

        writeAmounts(file, "Buy amount at ", histogramBuyPrecision);
        writeAmounts(file, "Sell amount at ", histogramSellPrecision);
        file.write(System.lineSeparator());
    }

    @SneakyThrows
    private void writeAmounts(Writer file, String name, int precision) {
        for (int i = 0; i < precision; ++i) {
            file.write(name + i + "\t");
        }
    }

    @SneakyThrows
    private void writeBook(Writer file, OrderBook book) {
        writeField(file, book.getMeta().getTimestamp());
        writeField(file, book.getBestBuy());
        writeField(file, book.getBestSell());
        writeField(file, book.getHistogramBuy()[0].getMaxPrice() - book.getHistogramBuy()[0].getMinPrice());
        writeField(file, book.getHistogramSell()[0].getMaxPrice() - book.getHistogramSell()[0].getMinPrice());

        Consumer<AggregatedOrder[]> writeHistogram = histogram -> {
            for (int i = 0; i < histogram.length; ++i) {
                writeField(file, histogram[i].getAmount(), i != histogram.length - 1);
            }
        };

        writeHistogram.accept(book.getHistogramBuy());
        file.write("\t");
        writeHistogram.accept(book.getHistogramSell());
        file.write(System.lineSeparator());
    }

    @SneakyThrows
    private <T> void writeField(Writer file, T value) {
        writeField(file, value, true);
    }

    @SneakyThrows
    private <T> void writeField(Writer file, T value, boolean hasSeparator) {
        file.write(String.valueOf(value) + (hasSeparator ? "\t" : ""));
    }

    @SneakyThrows
    private void zipFinishedDataIfNecessary() {
        for (Path path : listFilesToZip()) {
            Path toZip = path.getParent().resolve(path.getFileName().toString() + TO_ZIP);
            Files.move(path, toZip, REPLACE_EXISTING);
            String origName = path.getFileName().toString();
            try (GZIPOutputStream out = new GZIPOutputStream(
                    new FileOutputStream(path.getParent().resolve(origName + ".gz").toFile()))) {
                Files.copy(toZip, out);
            }
            Files.delete(toZip);
        }
    }

    @SneakyThrows
    private List<Path> listFilesToZip() {
        try (Stream<Path> pathStream = Files.list(Paths.get(cfg.getDir()))) {
            return pathStream
                    .filter(it -> it.toFile().isFile())
                    .filter(it -> {
                        String path = it.toString();
                        return !path.endsWith(TO_ZIP) && !path.endsWith(GZ)
                                && extractFromPath(path).compareTo(maxTime.get()) < 0;
                    })
                    .collect(Collectors.toList());
        }
    }

    private LocalDateTime extractFromPath(String path) {
        Matcher matcher = TIME_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return LocalDateTime.MIN;
        }

        String dateAndHour = matcher.group(1);
        return LocalDateTime.parse(dateAndHour + ":00", FULL_FORMAT);
    }

    private LocalDateTime utcDate() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String baseName(OrderBook book, String suffix) {
        return String.format("%s-%s_%s%s",
                book.getMeta().getPair().getFrom(),
                book.getMeta().getPair().getTo(),
                book.getMeta().getClient(),
                suffix
        );
    }

    private String getSuffixAndLockDate() {
        LocalDateTime date = utcDate();
        maxTime.set(date);
        return String.format("-%s.tsv", FORMAT.format(date));
    }
}
