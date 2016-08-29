package fr.delthas.so6rfc;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Generator {

	private static final int LINE_WIDTH = 80;
	private static final String TEAM_NAME = "Saucisse Royale";
	private static final String ID_PREFIX = "SO6RFC";
	private static final int INDENT_SPACES = 3;

	private Path input;
	private StringBuilder output = new StringBuilder(1000);
	private int depth;
	private int summarySavePos = -1;

	public static void main(String[] args) throws Exception {
		new Generator(Paths.get("src/main/resources/input.xml")).start();
	}

	public Generator(Path input) throws IOException {
		this.input = input;
	}

	private void start() throws Exception {
		InputStream is = new BufferedInputStream(Files.newInputStream(input));
		XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(is);
		while (reader.hasNext()) {
			XMLEvent event = reader.nextEvent();
			switch (event.getEventType()) {
			case XMLEvent.START_ELEMENT:
				StartElement start = event.asStartElement();
				switch (start.getName().getLocalPart()) {
				case "document":
					if (depth != 0) {
						throw new IllegalStateException();
					}
					output.append('\n').append('\n').append('\n');
					output.append(TEAM_NAME);
					String author = getAttribute(start, "author");
					appendNTimes(output, ' ', LINE_WIDTH - author.length() - TEAM_NAME.length());
					output.append(author).append('\n');
					String status = getAttribute(start, "status");
					output.append("Statut : ").append(status);
					String dateString = LocalDate.now(ZoneOffset.UTC)
							.format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.FRENCH));
					appendNTimes(output, ' ', LINE_WIDTH - dateString.length() - status.length() - "Statut : ".length());
					output.append(dateString).append('\n');
					output.append('\n').append('\n');
					String title = getAttribute(start, "title");
					output.append(centered(title)).append('\n');
					int id = Integer.parseInt(getAttribute(start, "id"));
					output.append(centered(ID_PREFIX + id)).append('\n');
					output.append('\n').append('\n');
					break;
				case "context":
					if (depth != 0) {
						throw new IllegalStateException();
					}
					output.append("Contexte : ").append('\n').append('\n');
					depth += INDENT_SPACES;
					break;
				case "summary":
					if (depth != 0) {
						throw new IllegalStateException();
					}
					summarySavePos = output.length();
					break;
				case "txt":
				case "tbl":
				case "ul":
				case "li":
				case "n":
					// TODO cases
				default:
				}
				break;
			case XMLEvent.END_ELEMENT:
				EndElement end = event.asEndElement();
				switch (end.getName().getLocalPart()) {
				case "document":
				case "context":
					depth -= INDENT_SPACES;
					break;
				default:
				}
				break;
			case XMLEvent.CHARACTERS:
				Characters chars = event.asCharacters();
				if (chars.isWhiteSpace())
					continue;
				// TODO (dépend de la node active)
				break;
			default:
			}
		}
		reader.close();
		is.close();
		if (summarySavePos != -1) {
			// TODO insert
		}
		System.out.println(output.toString());
	}

	private static final String getAttribute(StartElement start, String key) {
		Attribute attribute = start.getAttributeByName(new QName(key));
		if (attribute == null) {
			return null;
		}
		return attribute.getValue();
	}

	private static final String centered(String text) {
		if (text.length() > LINE_WIDTH) {
			throw new IllegalArgumentException();
		}
		StringBuilder sb = new StringBuilder(LINE_WIDTH);
		appendNTimes(sb, ' ', (LINE_WIDTH - text.length()) / 2);
		sb.append(text);
		return sb.toString();
	}

	private static final StringBuilder appendNTimes(StringBuilder sb, char c, int n) {
		for (int i = 0; i < n; i++) {
			sb.append(c);
		}
		return sb;
	}

}
