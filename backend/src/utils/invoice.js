const PDFDocument = require('pdfkit');
const config = require('../config');
const { round2 } = require('./http');

/**
 * Selling prices are GST-inclusive (standard for Indian B2C retail).
 * Taxable value = line_total / (1 + rate/100). Intra-state → CGST + SGST, inter-state → IGST.
 */
function taxBreakup(items, shippingState) {
  const intra = String(shippingState || '').trim().toLowerCase() === String(config.store.state).trim().toLowerCase();
  return items.map((it) => {
    const rate = Number(it.gst_rate) || 0;
    const gross = Number(it.line_total);
    const taxable = round2(gross / (1 + rate / 100));
    const tax = round2(gross - taxable);
    return {
      ...it, rate, taxable, tax,
      cgst: intra ? round2(tax / 2) : 0,
      sgst: intra ? round2(tax - round2(tax / 2)) : 0,
      igst: intra ? 0 : tax,
    };
  });
}

const inr = (n) => `Rs. ${Number(n).toFixed(2)}`;

/** Stream a GST invoice PDF for an order into `out` (an HTTP response or file stream). */
function renderInvoice(order, items, customer, out) {
  const addr = typeof order.shipping_address === 'string' ? JSON.parse(order.shipping_address) : order.shipping_address;
  const rows = taxBreakup(items, addr.state);
  const doc = new PDFDocument({ size: 'A4', margin: 40 });
  doc.pipe(out);

  const s = config.store;
  doc.fontSize(18).font('Helvetica-Bold').text('TAX INVOICE', { align: 'right' });
  doc.moveDown(0.3);
  doc.fontSize(13).text(s.name);
  doc.font('Helvetica').fontSize(9).text(s.address).text(`GSTIN: ${s.gstin}   State: ${s.state}`).text(`${s.phone}   ${s.email}`);

  doc.moveDown();
  const top = doc.y;
  doc.font('Helvetica-Bold').fontSize(10).text('Bill / Ship To', 40, top);
  doc.font('Helvetica').fontSize(9)
    .text(addr.name)
    .text([addr.line1, addr.line2, addr.landmark].filter(Boolean).join(', '))
    .text(`${addr.city}, ${addr.state} - ${addr.pincode}`)
    .text(`Phone: ${addr.phone}`);
  if (customer && customer.gstin) doc.text(`GSTIN: ${customer.gstin}`);

  doc.font('Helvetica-Bold').fontSize(10).text('Invoice Details', 340, top);
  doc.font('Helvetica').fontSize(9)
    .text(`Invoice No: ${order.invoice_number || order.order_number}`, 340)
    .text(`Order No: ${order.order_number}`, 340)
    .text(`Date: ${new Date(order.created_at).toLocaleDateString('en-IN')}`, 340)
    .text(`Payment: ${order.payment_method.toUpperCase()} (${order.payment_status})`, 340)
    .text(`Place of supply: ${addr.state}`, 340);

  // Table
  doc.moveDown(2);
  const cols = [
    { h: '#', w: 20 }, { h: 'Item', w: 150 }, { h: 'HSN', w: 45 }, { h: 'Qty', w: 30 },
    { h: 'Rate', w: 55 }, { h: 'Taxable', w: 60 }, { h: 'GST%', w: 35 }, { h: 'Tax', w: 50 }, { h: 'Total', w: 70 },
  ];
  let y = Math.max(doc.y, top + 90);
  const drawRow = (vals, bold) => {
    let x = 40;
    doc.font(bold ? 'Helvetica-Bold' : 'Helvetica').fontSize(8);
    const h = Math.max(...vals.map((v, i) => doc.heightOfString(String(v), { width: cols[i].w - 4 }))) + 6;
    if (y + h > 780) { doc.addPage(); y = 40; }
    vals.forEach((v, i) => {
      doc.text(String(v), x + 2, y + 3, { width: cols[i].w - 4, align: i >= 3 ? 'right' : 'left' });
      x += cols[i].w;
    });
    y += h;
    doc.moveTo(40, y).lineTo(555, y).strokeColor('#cccccc').stroke();
  };
  doc.moveTo(40, y).lineTo(555, y).strokeColor('#333333').stroke();
  drawRow(cols.map((c) => c.h), true);
  rows.forEach((r, i) =>
    drawRow([i + 1, `${r.product_name} (${r.variant_label})`, r.hsn_code || '-', r.quantity,
      Number(r.unit_price).toFixed(2), r.taxable.toFixed(2), r.rate, r.tax.toFixed(2), Number(r.line_total).toFixed(2)])
  );

  const sum = (k) => round2(rows.reduce((a, r) => a + r[k], 0));
  y += 10;
  const line = (label, val, bold) => {
    doc.font(bold ? 'Helvetica-Bold' : 'Helvetica').fontSize(9);
    doc.text(label, 340, y, { width: 120 });
    doc.text(val, 460, y, { width: 95, align: 'right' });
    y += 14;
  };
  line('Taxable value', inr(sum('taxable')));
  if (sum('igst') > 0) line('IGST', inr(sum('igst')));
  else { line('CGST', inr(sum('cgst'))); line('SGST', inr(sum('sgst'))); }
  line('Items total (incl. GST)', inr(order.subtotal));
  if (Number(order.discount) > 0) line(`Discount${order.coupon_code ? ` (${order.coupon_code})` : ''}`, `- ${inr(order.discount)}`);
  line('Delivery charges', inr(order.delivery_charge));
  line('Grand total', inr(order.total), true);

  doc.font('Helvetica').fontSize(8).fillColor('#666666')
    .text('This is a computer-generated invoice and does not require a signature.', 40, 790, { align: 'center', width: 515 });
  doc.end();
}

module.exports = { renderInvoice, taxBreakup };
