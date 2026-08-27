package org.openmrs.module.patienthandover.page.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.ui.framework.page.PageModel;

final class PaginationSupport {
	
	static final int PAGE_SIZE = 10;
	
	private PaginationSupport() {
	}
	
	static <T> List<T> list(List<T> rows, int requestedPage, PageModel model, String prefix) {
		int totalItems = rows == null ? 0 : rows.size();
		int totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);
		int page = Math.max(1, Math.min(requestedPage, totalPages));
		int from = Math.min((page - 1) * PAGE_SIZE, totalItems);
		int to = Math.min(from + PAGE_SIZE, totalItems);
		model.addAttribute(prefix + "CurrentPage", page);
		model.addAttribute(prefix + "TotalPages", totalPages);
		model.addAttribute(prefix + "TotalItems", totalItems);
		model.addAttribute(prefix + "HasPrevious", page > 1);
		model.addAttribute(prefix + "HasNext", page < totalPages);
		return rows == null ? new ArrayList<T>() : new ArrayList<T>(rows.subList(from, to));
	}
	
	static <K, V> Map<K, V> map(Map<K, V> rows, int requestedPage, PageModel model, String prefix) {
		List<Map.Entry<K, V>> entries = rows == null ? new ArrayList<Map.Entry<K, V>>() : new ArrayList<Map.Entry<K, V>>(
		        rows.entrySet());
		List<Map.Entry<K, V>> pageEntries = list(entries, requestedPage, model, prefix);
		Map<K, V> page = new LinkedHashMap<K, V>();
		for (Map.Entry<K, V> entry : pageEntries)
			page.put(entry.getKey(), entry.getValue());
		return page;
	}
}
