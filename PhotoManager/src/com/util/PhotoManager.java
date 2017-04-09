package com.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

public class PhotoManager {
  private static final Logger LOGGER = Logger.getLogger(PhotoManager.class.getName());

  public static void main(String[] args) throws IOException, ImageProcessingException {

    validateInputs(args);

    String sourcePath = args[0];
    LOGGER.info("Source Path:" + sourcePath);

    String destPath = args[1];
    LOGGER.info("Destination Path:" + destPath);

    String extensionFilters = getExtensionFilters(args);
    copyFiles(sourcePath, destPath, extensionFilters);
    LOGGER.info("Files copy completed.");
  }

  private static FileTime readTimeTaken(Path path) throws ImageProcessingException, IOException {
    FileTime ft = readMetaTimeTaken(path);
    if (ft == null) {
      BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
      return attr.creationTime();
    }
    return ft;
  }

  private static FileTime readMetaTimeTaken(Path path) throws ImageProcessingException, IOException {
    Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
    ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
    if (directory == null) {
      return null;
    }
    String dateStr = directory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
    if (dateStr == null) {
      return null;
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    LocalDateTime formatDateTime = LocalDateTime.parse(dateStr, formatter);
    ZonedDateTime zdt = formatDateTime.atZone(ZoneId.of("America/New_York"));
    return FileTime.fromMillis(zdt.toInstant().toEpochMilli());
  }


  private static String getExtensionFilters(String[] args) {
    String extensionFilters = "*";
    if (args.length > 2) {
      extensionFilters = "*.{" + args[2] + "}";
    }
    return extensionFilters;
  }

  private static void validateInputs(String[] args) throws IOException {
    if (args.length < 2) {
      LOGGER.info("Please provide source and destination folders.");
      System.exit(0);
    }
    if (Files.notExists(Paths.get(args[0]))) {
      LOGGER.info("Invalid source path.");
      System.exit(0);
    }
    if (Files.notExists(Paths.get(args[1]))) {
      Files.createDirectories(Paths.get(args[1]));
      LOGGER.info("Creating destination folder:" + args[1]);
    }
  }

  private static void copyFiles(String sourcePath, String destPath, String extensionFilters) throws IOException, ImageProcessingException {
    Path srcPath = FileSystems.getDefault().getPath(sourcePath);
    DirectoryStream<Path> stream = Files.newDirectoryStream(srcPath, extensionFilters);
    for (Path path : stream) {
      FileTime ft = readTimeTaken(path);
      String destFullPath = createFolders(destPath, ft);
      copyFile(path, ft, destFullPath);
    }
    stream.close();
  }

  private static String createFolders(String destPath, FileTime ft) throws IOException {
    String year = createYearFolder(destPath, ft);
    String monthFolder = createMonthFolder(destPath, ft, year);
    String destFullPath = createDateFolder(destPath, ft, year, monthFolder);
    return destFullPath;
  }

  private static void copyFile(Path path, FileTime ft, String destFullPath) throws IOException {
    Path dstPath = FileSystems.getDefault().getPath(destFullPath + "/" + path.getFileName());
    if (Files.notExists(dstPath)) {
      LOGGER.info("Copying file:" + path.getFileName());
      Files.copy(path, dstPath, StandardCopyOption.COPY_ATTRIBUTES);
      BasicFileAttributeView attributes = Files.getFileAttributeView(dstPath, BasicFileAttributeView.class);
      attributes.setTimes(ft, ft, ft);
    } else {
      LOGGER.info(path.getFileName() + " already exists.");
    }
  }

  private static String createDateFolder(String destPath, FileTime ft, String year, String monthFolder) throws IOException {
    SimpleDateFormat dfdate = new SimpleDateFormat("MM-dd-yyyy");
    String dateCreated = dfdate.format(ft.toMillis());
    String destFullPath = destPath + "/" + year + "/" + monthFolder + "/" + dateCreated;
    Files.createDirectories(Paths.get(destFullPath));
    return destFullPath;
  }

  private static String createMonthFolder(String destPath, FileTime ft, String year) throws IOException {
    SimpleDateFormat dfmon = new SimpleDateFormat("MMM");
    String monthStr = dfmon.format(ft.toMillis());

    SimpleDateFormat dfmm = new SimpleDateFormat("MM");
    String monthDigit = dfmm.format(ft.toMillis());
    String monthFolder = monthDigit + "-" + monthStr;
    Files.createDirectories(Paths.get(destPath + "/" + year + "/" + monthFolder));
    return monthFolder;
  }

  private static String createYearFolder(String destPath, FileTime ft) throws IOException {
    SimpleDateFormat dfyyyy = new SimpleDateFormat("yyyy");
    String year = dfyyyy.format(ft.toMillis());
    Files.createDirectories(Paths.get(destPath + "/" + year));
    return year;
  }

}
