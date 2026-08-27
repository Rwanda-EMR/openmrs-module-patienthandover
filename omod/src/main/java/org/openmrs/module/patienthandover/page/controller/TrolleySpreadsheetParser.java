package org.openmrs.module.patienthandover.page.controller;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class TrolleySpreadsheetParser {
	
	private TrolleySpreadsheetParser() {
	}
	
	static List<EmergencyTrolleyItem> parse(HttpServletRequest request, String batchUuid) {
		List<EmergencyTrolleyItem> items = new ArrayList<EmergencyTrolleyItem>();
		addManualRows(items, request, batchUuid);
		if (request instanceof MultipartHttpServletRequest) {
			MultipartFile file = ((MultipartHttpServletRequest) request).getFile("trolleyFile");
			if (file != null && !file.isEmpty())
				addFileRows(items, file, batchUuid);
		}
		return items;
	}
	
	private static void addManualRows(List<EmergencyTrolleyItem> items, HttpServletRequest request, String batchUuid) {
		String[] names = request.getParameterValues("trolleyItemName");
		String[] quantities = request.getParameterValues("trolleyRemainingQuantity");
		String[] units = request.getParameterValues("trolleyUnit");
		String[] expiries = request.getParameterValues("trolleyExpiryDate");
		String[] remarks = request.getParameterValues("trolleyRemarks");
		if (names == null)
			return;
		for (int i = 0; i < names.length; i++) {
			String name = value(names, i);
			if (!name.isEmpty())
				items.add(create(batchUuid, name, value(quantities, i), value(units, i), value(expiries, i),
				    value(remarks, i)));
		}
	}
	
	private static void addFileRows(List<EmergencyTrolleyItem> items, MultipartFile file, String batchUuid) {
		String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
		if (file.getSize() > 5L * 1024L * 1024L)
			throw new IllegalArgumentException("The trolley spreadsheet must be 5 MB or smaller.");
		try {
			if (filename.endsWith(".csv"))
				readCsv(items, file, batchUuid);
			else if (filename.endsWith(".xlsx"))
				readXlsx(items, file, batchUuid);
			else
				throw new IllegalArgumentException("Upload an Excel .xlsx file or a .csv file.");
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException(
			        "The trolley spreadsheet could not be read. Use the displayed five-column format.");
		}
	}
	
	private static void readCsv(List<EmergencyTrolleyItem> items, MultipartFile file, String batchUuid) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"));
		String line;
		boolean first = true;
		while ((line = reader.readLine()) != null) {
			List<String> cells = splitCsv(line);
			if (first && !cells.isEmpty() && cells.get(0).toLowerCase().contains("item")) {
				first = false;
				continue;
			}
			first = false;
			addRow(items, batchUuid, cells);
		}
	}
	
	private static void readXlsx(List<EmergencyTrolleyItem> items, MultipartFile file, String batchUuid) throws Exception {
		Map<String, byte[]> entries = new HashMap<String, byte[]>();
		ZipInputStream zip = new ZipInputStream(file.getInputStream());
		ZipEntry entry;
		byte[] buffer = new byte[8192];
		while ((entry = zip.getNextEntry()) != null) {
			if (!entry.isDirectory()
			        && (entry.getName().equals("xl/sharedStrings.xml") || entry.getName().equals("xl/worksheets/sheet1.xml"))) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				int count;
				while ((count = zip.read(buffer)) > 0)
					output.write(buffer, 0, count);
				entries.put(entry.getName(), output.toByteArray());
			}
		}
		byte[] sheetData = entries.get("xl/worksheets/sheet1.xml");
		if (sheetData == null)
			throw new IllegalArgumentException("The Excel file has no first worksheet.");
		List<String> shared = readSharedStrings(entries.get("xl/sharedStrings.xml"));
		Document sheet = document(sheetData);
		NodeList rows = sheet.getElementsByTagNameNS("*", "row");
		for (int rowIndex = 0; rowIndex < rows.getLength(); rowIndex++) {
			Element row = (Element) rows.item(rowIndex);
			String[] values = new String[5];
			NodeList cells = row.getElementsByTagNameNS("*", "c");
			for (int i = 0; i < cells.getLength(); i++) {
				Element cell = (Element) cells.item(i);
				int column = columnIndex(cell.getAttribute("r"));
				if (column >= 0 && column < values.length)
					values[column] = cellValue(cell, shared);
			}
			List<String> cellsList = new ArrayList<String>();
			for (String value : values)
				cellsList.add(value == null ? "" : value.trim());
			if (rowIndex == 0 && cellsList.get(0).toLowerCase().contains("item"))
				continue;
			addRow(items, batchUuid, cellsList);
		}
	}
	
	private static List<String> readSharedStrings(byte[] data) throws Exception {
		List<String> values = new ArrayList<String>();
		if (data == null)
			return values;
		Document document = document(data);
		NodeList strings = document.getElementsByTagNameNS("*", "si");
		for (int i = 0; i < strings.getLength(); i++)
			values.add(strings.item(i).getTextContent());
		return values;
	}
	
	private static Document document(byte[] data) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(data));
	}
	
	private static String cellValue(Element cell, List<String> shared) {
		String type = cell.getAttribute("t");
		if ("inlineStr".equals(type))
			return cell.getTextContent();
		NodeList values = cell.getElementsByTagNameNS("*", "v");
		if (values.getLength() == 0)
			return "";
		String value = values.item(0).getTextContent();
		if ("s".equals(type)) {
			try {
				int index = Integer.parseInt(value);
				return index >= 0 && index < shared.size() ? shared.get(index) : "";
			}
			catch (NumberFormatException ignored) {
				return "";
			}
		}
		return value;
	}
	
	private static int columnIndex(String reference) {
		int result = 0;
		boolean found = false;
		for (int i = 0; i < reference.length(); i++) {
			char c = Character.toUpperCase(reference.charAt(i));
			if (c < 'A' || c > 'Z')
				break;
			result = result * 26 + (c - 'A' + 1);
			found = true;
		}
		return found ? result - 1 : -1;
	}
	
	private static void addRow(List<EmergencyTrolleyItem> items, String batchUuid, List<String> cells) {
		String name = cell(cells, 0);
		if (!name.isEmpty())
			items.add(create(batchUuid, name, cell(cells, 1), cell(cells, 2), cell(cells, 3), cell(cells, 4)));
	}
	
	private static EmergencyTrolleyItem create(String batchUuid, String name, String quantity, String unit, String expiry,
	        String remarks) {
		if (quantity.isEmpty())
			throw new IllegalArgumentException("Enter a remaining quantity for trolley item " + name + ".");
		double parsed;
		try {
			parsed = Double.parseDouble(quantity.replace(",", ""));
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Remaining quantity for " + name + " is invalid.");
		}
		if (parsed < 0)
			throw new IllegalArgumentException("Remaining quantity cannot be negative for " + name + ".");
		EmergencyTrolleyItem item = new EmergencyTrolleyItem();
		item.setUuid(UUID.randomUUID().toString());
		item.setBatchUuid(batchUuid);
		item.setItemName(name.trim());
		item.setRemainingQuantity(parsed);
		item.setUnit(emptyToNull(unit));
		item.setExpiryDate(parseDate(expiry, name));
		item.setRemarks(emptyToNull(remarks));
		item.setCreator(Context.getAuthenticatedUser());
		item.setDateCreated(new Date());
		return item;
	}
	
	private static Date parseDate(String value, String name) {
		if (value == null || value.trim().isEmpty())
			return null;
		String normalized = value.trim();
		String[] patterns = { "yyyy-MM-dd", "M/d/yyyy", "d/M/yyyy" };
		for (String pattern : patterns)
			try {
				return new SimpleDateFormat(pattern).parse(normalized);
			}
			catch (ParseException ignored) {}
		try {
			double serial = Double.parseDouble(normalized);
			long milliseconds = Math.round((serial - 25569d) * 86400000d);
			return new Date(milliseconds);
		}
		catch (NumberFormatException ignored) {
			throw new IllegalArgumentException("Expiry date for " + name + " is invalid.");
		}
	}
	
	private static List<String> splitCsv(String line) {
		List<String> cells = new ArrayList<String>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					value.append('"');
					i++;
				} else
					quoted = !quoted;
			} else if (c == ',' && !quoted) {
				cells.add(value.toString().trim());
				value.setLength(0);
			} else
				value.append(c);
		}
		cells.add(value.toString().trim());
		return cells;
	}
	
	private static String value(String[] values, int i) {
		return values == null || i >= values.length || values[i] == null ? "" : values[i].trim();
	}
	
	private static String cell(List<String> values, int i) {
		return i < values.size() && values.get(i) != null ? values.get(i).trim() : "";
	}
	
	private static String emptyToNull(String value) {
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}
}
