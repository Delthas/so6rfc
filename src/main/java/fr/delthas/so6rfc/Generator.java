package fr.delthas.so6rfc;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Generator {

  private static final Pattern patternTextRemoveNewlines = Pattern.compile("\\R\\h+");
  private static final int LINE_WIDTH = 80;
  private static final String TEAM_NAME = "Saucisse Royale";
  private static final String ID_PREFIX = "SO6RFC";
  private static final String LIST_ITEM_PREFIX = "o  ";
  private static final int INDENT_SPACES = 3;

  private ArrayDeque<Integer> parts = new ArrayDeque<>();
  private Path input;
  private StringBuilder output = new StringBuilder(1000);
  private StringBuilder summary = new StringBuilder();
  private int depth;
  private int summarySavePos = -1;
  {
    parts.addLast(0);
    summary.append("Table des matières").append('\n').append('\n');
  }

  public static void main(String[] args) throws Exception {
    new Generator(Paths.get("src/main/resources/input.xml")).start();
  }

  public Generator(Path input) {
    this.input = input;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void start() throws Exception {
    InputStream is = new BufferedInputStream(Files.newInputStream(input));
    XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(is);
    while (reader.hasNext()) {
      XMLEvent event = nextEvent(reader);
      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          StartElement start = event.asStartElement();
          switch (start.getName().getLocalPart()) {
            case "document":
              if (depth != 0) {
                throw new IllegalStateException();
              }
              appendNTimes(output, '\n', 3);
              output.append(TEAM_NAME);
              String author = getAttribute(start, "author");
              appendNTimes(output, ' ', LINE_WIDTH - author.length() - TEAM_NAME.length());
              output.append(author).append('\n');
              String status = getAttribute(start, "status");
              output.append("Statut : ").append(status);
              String dateString = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.FRENCH));
              appendNTimes(output, ' ', LINE_WIDTH - dateString.length() - status.length() - "Statut : ".length());
              output.append(dateString).append('\n');
              output.append('\n').append('\n');
              String title = getAttribute(start, "title");
              appendText(output, title, depth, true, false);
              int id = Integer.parseInt(getAttribute(start, "id"));
              appendText(output, ID_PREFIX + id, depth, true, false);
              output.append('\n').append('\n');
              summarySavePos = output.length();
              break;
            case "context":
              if (depth != 0) {
                throw new IllegalStateException();
              }
              output.append("Contexte").append('\n').append('\n');
              depth += INDENT_SPACES;
              break;
            case "txt":
              String text = reader.getElementText();
              text = patternTextRemoveNewlines.matcher(text).replaceAll(" ").trim();
              appendText(output, text, depth, false, false);
              output.append('\n');
              break;
            case "tbl":
            case "ul":
              // on attend des li
              String compactString = getAttribute(start, "compact");
              boolean compact;
              if (compactString == null || compactString.equalsIgnoreCase("false")) {
                compact = false;
              } else if (compactString.equalsIgnoreCase("true")) {
                compact = true;
              } else {
                throw new IllegalArgumentException("Unknown compact argument");
              }
              while (true) {
                XMLEvent newEvent = nextEvent(reader);
                if (newEvent.isEndElement()) {
                  break;
                }
                if (!newEvent.isStartElement() || !newEvent.asStartElement().getName().getLocalPart().equalsIgnoreCase("li")) {
                  throw new IllegalArgumentException("Unknown tag in ul (only li is ok)");
                }
                String line = reader.getElementText();
                line = patternTextRemoveNewlines.matcher(line).replaceAll(" ").trim();
                reader.nextEvent();
                appendText(output, LIST_ITEM_PREFIX + line, depth, false, true);
                if (!compact) {
                  output.append('\n');
                }
              }
              if (compact) {
                output.append('\n');
              }
              break;
            case "n":
              output.append('\n');
              break;
            case "part":
              String name = getAttribute(start, "name");
              if (name == null) {
                throw new IllegalArgumentException("No name set");
              }
              compactString = getAttribute(start, "compact");
              if (compactString == null || compactString.equalsIgnoreCase("false")) {
                compact = false;
              } else if (compactString.equalsIgnoreCase("true")) {
                compact = true;
              } else {
                throw new IllegalArgumentException("Unknown compact argument");
              }
              StringBuilder partLine = new StringBuilder();
              int last = parts.removeLast();
              parts.addLast(++last);
              appendNTimes(summary, ' ', INDENT_SPACES * parts.size());
              for (Integer part : parts) {
                partLine.append(part).append('.');
              }
              partLine.append(' ').append(' ').append(name);
              appendText(summary, partLine, 0, false, false);
              parts.addLast(0);
              appendText(output, partLine.toString(), depth, false, false);
              output.append('\n');
              depth += INDENT_SPACES;
              break;
            case "table":
              String tableName = getAttribute(start, "name");
              if (tableName == null) {
                throw new IllegalArgumentException("No table name set");
              }
              XMLEvent newEvent = nextEvent(reader);
              if (!newEvent.isStartElement() || !newEvent.asStartElement().getName().getLocalPart().equalsIgnoreCase("header")) {
                throw new IllegalArgumentException("First tag in list should be header");
              }
              reader.nextEvent();
              List<String> headersList = new ArrayList<>();
              newEvent.asStartElement().getAttributes().forEachRemaining(a -> headersList.add(((Attribute) a).getValue()));
              String[] headers = headersList.toArray(new String[0]);
              List<String[][]> values = new ArrayList<>(headers.length);
              while (true) {
                newEvent = nextEvent(reader);
                if (newEvent.isEndElement()) {
                  break;
                }
                if (!newEvent.isStartElement() || !newEvent.asStartElement().getName().getLocalPart().equalsIgnoreCase("row")) {
                  throw new IllegalArgumentException("Tags in list should be row");
                }
                reader.nextEvent();
                Iterator it = newEvent.asStartElement().getAttributes();
                String[][] row = new String[headers.length][];
                values.add(row);
                for (int i = 0; i < headers.length; i++) {
                  row[i] = ((Attribute) it.next()).getValue().split("\\|");
                }
              }
              int[] sizes = new int[headers.length];
              for (int i = 0; i < sizes.length; i++) {
                sizes[i] = headers[i].length() + 2;
              }
              for (String[][] s_ : values) {
                for (int i = 0; i < headers.length; i++) {
                  for (String s : s_[i]) {
                    if (s.length() + 2 > sizes[i]) {
                      sizes[i] = s.length() + 2;
                    }
                  }
                }
              }
              int fullSize = sizes.length + IntStream.of(sizes).sum() + 1;
              if (fullSize > LINE_WIDTH) {
                throw new IllegalArgumentException("Table \"" + tableName + "\" is too large");
              }
              int spaces = (LINE_WIDTH - fullSize) / 2;
              appendNTimes(output, ' ', spaces);
              output.append('+');
              for (int size : sizes) {
                appendNTimes(output, '-', size);
                output.append('+');
              }
              output.append('\n');
              appendNTimes(output, ' ', spaces);
              output.append('|');
              for (int i = 0; i < sizes.length; i++) {
                String header = headers[i];
                int leftSpaces = (sizes[i] - header.length()) / 2;
                appendNTimes(output, ' ', leftSpaces);
                output.append(header);
                appendNTimes(output, ' ', sizes[i] - leftSpaces - header.length());
                output.append('|');
              }
              output.append('\n');
              appendNTimes(output, ' ', spaces);
              output.append('+');
              for (int size : sizes) {
                appendNTimes(output, '-', size);
                output.append('+');
              }
              output.append('\n');
              for (Iterator<String[][]> it = values.iterator(); it.hasNext();) {
                String[][] row = it.next();
                for (int i = 0;; i++) {
                  boolean empty = true;
                  for (int col = 0; col < headers.length; col++) {
                    if (row[col].length > i) {
                      empty = false;
                      break;
                    }
                  }
                  if (empty) {
                    if (it.hasNext()) {
                      appendNTimes(output, ' ', spaces);
                      output.append('|');
                      for (int size : sizes) {
                        appendNTimes(output, ' ', size);
                        output.append('|');
                      }
                      output.append('\n');
                    }
                    break;
                  }
                  appendNTimes(output, ' ', spaces);
                  output.append('|');
                  for (int col = 0; col < headers.length; col++) {
                    String value;
                    if (row[col].length <= i) {
                      value = "";
                    } else {
                      value = row[col][i];
                    }
                    int leftSpaces = (sizes[col] - value.length()) / 2;
                    appendNTimes(output, ' ', leftSpaces);
                    output.append(value);
                    appendNTimes(output, ' ', sizes[col] - leftSpaces - value.length());
                    output.append('|');
                  }
                  output.append('\n');
                }
              }
              appendNTimes(output, ' ', spaces);
              output.append('+');
              for (int size : sizes) {
                appendNTimes(output, '-', size);
                output.append('+');
              }
              output.append('\n');
              output.append('\n');
              appendText(output, tableName, 0, true, false);
              output.append('\n');
              output.append('\n');
              break;
            default:
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          EndElement end = event.asEndElement();
          switch (end.getName().getLocalPart()) {
            case "document":
              depth -= INDENT_SPACES;
              break;
            case "context":
              depth -= INDENT_SPACES;
              addWhitespace(output, 2);
              summarySavePos = output.length();
              break;
            case "part":
              parts.removeLast();
              addWhitespace(output, 2);
              depth -= INDENT_SPACES;
              break;
            default:
          }
          break;
        default:
      }
    }
    reader.close();
    is.close();
    summary.append('\n').append('\n');
    output.insert(summarySavePos, summary);
    System.out.println(output.toString());
  }

  private static final String getAttribute(StartElement start, String key) {
    Attribute attribute = start.getAttributeByName(new QName(key));
    if (attribute == null) {
      return null;
    }
    return attribute.getValue();
  }

  private static void appendText(StringBuilder sb, CharSequence text, int depth, boolean centered, boolean depthOnNewLine) {
    boolean first = true;
    for (int start = 0; start < text.length();) {
      if (text.length() - start <= LINE_WIDTH - depth) {
        appendNTimes(sb, ' ', (centered ? (LINE_WIDTH - depth - text.length() + start) / 2 : depth) + (!first && depthOnNewLine ? INDENT_SPACES : 0));
        sb.append(text, start, text.length());
        sb.append('\n');
        return;
      }
      int end;
      for (end = LINE_WIDTH - depth + start; end >= start && text.charAt(end) != ' '; end--) {
      }
      if (start >= end) {
        throw new IllegalArgumentException("Row is too long");
      }
      appendNTimes(sb, ' ', centered ? (LINE_WIDTH - depth - end + start) / 2 : depth + (!first && depthOnNewLine ? INDENT_SPACES : 0));
      sb.append(text, start, end);
      sb.append('\n');
      start = end + 1;
      first = false;
    }
  }

  private static final StringBuilder appendNTimes(StringBuilder sb, char c, int n) {
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb;
  }

  private static final StringBuilder addWhitespace(StringBuilder sb, int minimumWhitespace) {
    if (sb.charAt(sb.length() - 1) != '\n') {
      throw new IllegalArgumentException("Whitespace requested with col!=0");
    }
    int count;
    for (count = 0; count <= sb.length() - 1 && sb.charAt(sb.length() - 1 - count) == '\n'; count++) {
    }
    count--;
    if (minimumWhitespace > count) {
      appendNTimes(sb, '\n', minimumWhitespace - count);
    }
    return sb;
  }

  private static final XMLEvent nextEvent(XMLEventReader reader) throws XMLStreamException {
    XMLEvent event;
    do {
      event = reader.nextEvent();
    } while (event.isCharacters() && event.asCharacters().isWhiteSpace());
    return event;
  }

}
