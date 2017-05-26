/*
 * Copyright 2011-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.camaral.sheeco;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.github.camaral.sheeco.annotation.SpreadsheetPayload;
import com.github.camaral.sheeco.exceptions.SpreadsheetUnmarshallingException;
import com.github.camaral.sheeco.exceptions.SpreasheetUnmarshallingUnrecoverableException;
import com.github.camaral.sheeco.processor.Payload;
import com.github.camaral.sheeco.processor.PayloadContext;
import com.github.camaral.sheeco.processor.PayloadFiller;

/**
 * @author caio.amaral
 */
public class Sheeco {
	/**
	 * Max number of blank consecutive rows
	 */
	private static final int MAX_BLANK_ROWS = 50;

	/**
	 * Creates a list of Java objects from a the spreadsheet. The payloadClass
	 * must be annotated with {@link SpreadsheetPayload}.
	 * 
	 * @param file
	 *            The spreadsheet file
	 * @param payloadClass
	 *            The type of the Java objects to be created
	 * @return the content of the spreadsheet serialized into a list of java
	 *         objects
	 * @throws SpreadsheetUnmarshallingException
	 * @throws SpreasheetUnmarshallingUnrecoverableException
	 */
	public <T> List<T> fromSpreadsheet(final File file,
			final Class<T> payloadClass)
			throws SpreadsheetUnmarshallingException,
			SpreasheetUnmarshallingUnrecoverableException {
		InputStream stream = null;
		try {
			stream = new BufferedInputStream(new FileInputStream(file));
			return fromSpreadsheet(stream, payloadClass);
		} catch (final FileNotFoundException e) {
			throw new SpreasheetUnmarshallingUnrecoverableException(
					String.format("sheeco.serializer.file.cannot.open"));
		} finally {
			close(stream);
		}
	}

	/**
	 * Creates a list of Java objects from a the spreadsheet. The payloadClass
	 * must be annotated with {@link SpreadsheetPayload}.
	 * 
	 * @param stream
	 *            The spreadsheet file
	 * @param payloadClass
	 *            The type of the Java objects to be created
	 * @return the content of the spreadsheet serialized into a list of java
	 *         objects.
	 * @throws SpreadsheetUnmarshallingException
	 * @throws SpreasheetUnmarshallingUnrecoverableException
	 */
	public <T> List<T> fromSpreadsheet(final InputStream stream,
			final Class<T> payloadClass)
			throws SpreadsheetUnmarshallingException,
			SpreasheetUnmarshallingUnrecoverableException {
		try {
			final Workbook wb = WorkbookFactory.create(stream);
			final Payload<T> payload = new Payload<>(payloadClass);

			final Sheet sheet = getSheet(payload.getName(), wb);

			final FormulaEvaluator evaluator = wb.getCreationHelper()
					.createFormulaEvaluator();

			final PayloadContext<T> ctx = new PayloadContext<>(sheet,
					evaluator, payload);
			final List<T> payloads = readPayloads(ctx);

			if (ctx.getViolations().isEmpty()) {
				return payloads;
			} else {
				throw new SpreadsheetUnmarshallingException(payloads,
						ctx.getViolations());
			}

		} catch (final FileNotFoundException e) {
			throw new SpreasheetUnmarshallingUnrecoverableException(
					String.format("sheeco.serializer.file.cannot.open"));
		} catch (final IOException | InvalidFormatException e) {
			throw new SpreasheetUnmarshallingUnrecoverableException(
					String.format("sheeco.serializer.file.wrong.format"));
		}
	}

	/**
	 * Creates a spreadsheet with only the headers as described by the set of
	 * payloadClass, but no data. For each payloadClass a sheet will be created.
	 * 
	 * @param payloadClass
	 *            Java types annotated with {@link SpreadsheetPayload}
	 * @param stream
	 *            The output which will receive the content of the spreadsheet
	 */
	public void toSpreadsheet(final Set<Class<?>> payloadClass,
			final OutputStream stream) {
		throw new RuntimeException("Not Implemented");
	}

	/**
	 * Creates a spreadsheet with the headers and the content of the payload
	 * objects.
	 * 
	 * @param payloads
	 *            Objects of a Java type annotated with
	 *            {@link SpreadsheetPayload}
	 * @param out
	 *            The output which will receive the content of the spreadsheet
	 */
	public void toSpreadsheet(final List<? extends Object> payloads,
			final OutputStream out) {
		throw new RuntimeException("Not Implemented");
	}

	private <T> Sheet getSheet(final String sheetName, final Workbook wb)
			throws AssertionError {
		final Sheet sheet = wb.getSheet(sheetName);

		if (sheet == null) {
			throw new SpreasheetUnmarshallingUnrecoverableException(
					"Spreadsheet does not contain a sheet named with \""
							+ sheetName + "\"");
		}

		return sheet;
	}

	private <T> List<T> readPayloads(final PayloadContext<T> ctx) {
		final List<T> payloads = new ArrayList<>();

		final int firstRowNum = ctx.getSheet().getFirstRowNum();
		int blankRowCount = 0;

		for (final Row row : ctx.getSheet()) {
			if (row.getRowNum() == firstRowNum) {
				// First row reserved for headers
				continue;
			}
			if (!isBlankRow(row)) {
				blankRowCount = 0;

				payloads.add(readPayload(row, ctx));

			} else {
				// if the blank rows limit is reached, throws an exception
				++blankRowCount;
				if (blankRowCount >= MAX_BLANK_ROWS) {
					throw new SpreasheetUnmarshallingUnrecoverableException(
							"serializer.spreadsheet.row.too.many.blank");
				}
			}
		}

		return payloads;
	}

	private <T> T readPayload(final Row row, final PayloadContext<T> ctx) {
		final T payload = ctx.getPayload().newInstance();

		PayloadFiller.fillAttributes(payload, row, ctx);
		PayloadFiller.fillElements(payload, row, ctx);

		return payload;
	}

	private static void close(final InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (final IOException e) {
				throw new SpreasheetUnmarshallingUnrecoverableException(
						String.format("sheeco.serializer.file.cannot.close"));
			}
		}
	}

	private boolean isBlankRow(final Row row) {
		for (int i = row.getFirstCellNum(); i <= row.getLastCellNum(); i++) {
			final Cell cell = row.getCell(i);
			if (cell != null
					&& row.getCell(i).getCellType() != Cell.CELL_TYPE_BLANK) {
				return false;
			}
		}
		return true;
	}
}
