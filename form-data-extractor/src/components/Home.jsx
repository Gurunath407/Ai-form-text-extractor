import axios from 'axios';
import React, { useState, useRef, useEffect } from 'react';

// Helper to flatten nested JSON into key-value pairs using ONLY leaf keys (e.g. "city", not "address.city")
function flattenFieldsUseOnlyLeafKeys(obj, acc = {}, keySet = new Set()) {
  function helper(value, parentKeys = []) {
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      Object.keys(value).length > 0
    ) {
      Object.entries(value).forEach(([k, v]) => helper(v, [...parentKeys, k]));
    } else {
      const leafKey = parentKeys[parentKeys.length - 1];
      if (leafKey in acc) {
        if (typeof acc[leafKey] === 'string') {
          acc[leafKey] = [acc[leafKey], value ?? ''].filter(x => x !== '').join(', ');
        } else if (Array.isArray(acc[leafKey])) {
          acc[leafKey].push(value ?? '');
        }
      } else {
        acc[leafKey] = value ?? '';
        keySet.add(leafKey);
      }
    }
  }
  helper(obj, []);
  return acc;
}

// This function reorders allKeys to ensure "first name" and "last name"/"name"/"firstname"/"lastname" columns come directly after "Page #"
function sortNameColumns(allKeys) {
  const nameOptions = [
    // All lowercase for case-insensitive matching
    ['first name', 'firstname', 'first'],
    ['last name', 'lastname', 'last'],
    ['name']
  ];
  const mapKey = k => k.trim().replace(/_/g, ' ').toLowerCase();
  let firstMatched = null,
    lastMatched = null,
    nameMatched = null;

  for (const c of allKeys) {
    const norm = mapKey(c);
    if (!firstMatched && nameOptions[0].includes(norm)) firstMatched = c;
    if (!lastMatched && nameOptions[1].includes(norm)) lastMatched = c;
    if (!nameMatched && nameOptions[2].includes(norm)) nameMatched = c;
  }
  // Arrange: [first, last, name, ...otherKeysWithoutFirstLastName]
  // Also: do NOT repeat the columns if not present.
  let fixedOrder = [];
  if (firstMatched) fixedOrder.push(firstMatched);
  if (lastMatched) fixedOrder.push(lastMatched);
  if (nameMatched) fixedOrder.push(nameMatched);

  // Remove those from allKeys, then append the rest in original sort order.
  const rest = allKeys.filter(
    k =>
      k !== firstMatched &&
      k !== lastMatched &&
      k !== nameMatched
  );
  return [...fixedOrder, ...rest];
}

const Home = () => {
  // === ORIGINAL STATE (Upload / Extraction) ===
  const [image, setImage] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [messages, setMessages] = useState([]);
  const [progress, setProgress] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);
  const [extractionData, setExtractionData] = useState(null);
  const [viewMode, setViewMode] = useState('json');
  const [history, setHistory] = useState([]);
  const [runId, setRunId] = useState(null);
  const fileInputRef = useRef(null);

  // === EXTRACTION MANAGEMENT STATE ===
  const [extractions, setExtractions] = useState([]);
  const [loadingExtractions, setLoadingExtractions] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [currentEdit, setCurrentEdit] = useState(null);
  const [tempEditData, setTempEditData] = useState({});

  // === NEW: VIEW MODAL STATE ===
  const [viewModalOpen, setViewModalOpen] = useState(false);
  const [currentViewData, setCurrentViewData] = useState(null);
  const [viewModeModal, setViewModeModal] = useState('json');

  // === Refs for Auto Scroll ===
  const messagesContainerRef = useRef(null);

  // === Helpers ===
  const addMessage = async (text, delayMs = 400) => {
    if (delayMs > 0) await new Promise(res => setTimeout(res, delayMs));
    setMessages(prev => [...prev, { id: Date.now() + Math.random(), text, timestamp: new Date().toLocaleTimeString() }]);
    setTimeout(() => {
      if (messagesContainerRef.current) {
        messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
      }
    }, 100);
  };

  const isEmptyResult = (data) => {
    if (!data) return true;
    return Object.keys(data).length === 0;
  };

  const uploadImage = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axios.post('http://localhost:8080/v1/uploads', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data.runId;
  };

  const startExtraction = async (runId) => {
    const response = await axios.get(`http://localhost:8080/v1/runs/${runId}`);
    return response.data;
  };

  const getExtractionResult = async (runId, format = 'json') => {
    const response = await axios.post(
      'http://localhost:8080/v1/exports',
      { runId, format },
      {
        headers: { 'Content-Type': 'application/json' },
        responseType: format === 'csv' ? 'text' : 'json',
      }
    );
    return response.data;
  };

  const fetchAllExtractions = async () => {
    setLoadingExtractions(true);
    try {
      const response = await axios.get('http://localhost:8080/v1/extractions');
      setExtractions(response.data);
    } catch (err) {
      addMessage(`Failed to load extractions: ${err.message}`);
    } finally {
      setLoadingExtractions(false);
    }
  };

  const deleteExtraction = async (id) => {
    try {
      await axios.delete(`http://localhost:8080/v1/extractions/${id}`);
      fetchAllExtractions();
      addMessage('Extraction deleted.');
    } catch (err) {
      addMessage(`Delete failed: ${err.message}`);
    }
  };

  const deleteAllExtractions = async () => {
    if (!window.confirm('Delete all extractions? This cannot be undone.')) return;
    try {
      await axios.delete('http://localhost:8080/v1/extractions');
      fetchAllExtractions();
      addMessage('All extractions deleted.');
    } catch (err) {
      addMessage(`Delete all failed: ${err.message}`);
    }
  };

  const updateExtraction = async () => {
    try {
      JSON.parse(tempEditData.resultJson);
      await axios.put(`http://localhost:8080/v1/extractions/${currentEdit.id}`, tempEditData);
      setEditModalOpen(false);
      fetchAllExtractions();
      addMessage('Extraction updated.');

      if (currentEdit.runId === runId) {
        try {
          const updatedResult = JSON.parse(tempEditData.resultJson);
          setExtractionData(updatedResult);
        } catch (e) {
          addMessage('Could not parse updated JSON for display.');
        }
      }
    } catch (err) {
      if (err instanceof SyntaxError) {
        addMessage('Invalid JSON in result field.');
      } else {
        addMessage(`Update failed: ${err.message}`);
      }
    }
  };

  const openEditModal = (extraction) => {
    setCurrentEdit(extraction);
    try {
      const parsed = JSON.parse(extraction.resultJson);
      setTempEditData({
        documentType: extraction.documentType,
        resultJson: JSON.stringify(parsed, null, 2),
        avgConfidence: extraction.avgConfidence,
        runId: extraction.runId
      });
    } catch (e) {
      setTempEditData({
        documentType: extraction.documentType,
        resultJson: extraction.resultJson,
        avgConfidence: extraction.avgConfidence,
        runId: extraction.runId
      });
    }
    setEditModalOpen(true);
  };

  const openViewModal = (extraction) => {
    try {
      const parsed = JSON.parse(extraction.resultJson);
      setCurrentViewData(parsed);
    } catch (e) {
      setCurrentViewData(null);
    }
    setViewModeModal('json');
    setViewModalOpen(true);
  };

  const handleExtract = async () => {
    if (!image) return;

    setIsProcessing(true);
    setProgress(0);
    setMessages([]);
    setExtractionData(null);

    try {
      await addMessage('Image upload started...');
      setProgress(10);

      const newRunId = await uploadImage(image);
      setRunId(newRunId);

      await addMessage('Image upload completed.');
      setProgress(20);
      await addMessage(`Run ID: ${newRunId}`);
      setProgress(30);
      await addMessage('Extraction started...');
      setProgress(40);

      await startExtraction(newRunId);
      await addMessage('Processing...');
      setProgress(60);

      let result = null;
      let tries = 0;
      while (true) {
        await new Promise(resolve => setTimeout(resolve, 1400));
        try {
          result = await getExtractionResult(newRunId, 'json');
          break;
        } catch (error) {
          tries += 1;
          if (tries > 100) throw new Error('Timeout waiting for result.');
        }
      }

      await addMessage('Generating response...');
      setProgress(80);
      await new Promise(res => setTimeout(res, 1200));

      if (isEmptyResult(result)) {
        await addMessage('Extraction failed: No valid data found.');
        setExtractionData(null);
        setHistory(prev => [{
          id: newRunId,
          timestamp: new Date().toLocaleString(),
          status: 'failed'
        }, ...prev]);
      } else {
        setExtractionData(result);
        await addMessage('Extraction completed!');
        setHistory(prev => [{
          id: newRunId,
          timestamp: new Date().toLocaleString(),
          status: 'completed'
        }, ...prev]);
      }

      setProgress(100);
      fetchAllExtractions();
    } catch (error) {
      await addMessage(`Error: ${error.message}`);
      if (runId) {
        setHistory(prev => [{
          id: runId,
          timestamp: new Date().toLocaleString(),
          status: 'failed'
        }, ...prev]);
      }
      setExtractionData(null);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      setImage(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleCancel = () => {
    setImage(null);
    setPreviewUrl('');
    setMessages([]);
    setProgress(0);
    setExtractionData(null);
    setRunId(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const downloadData = async (format) => {
    if (!runId) return;
    try {
      const data = await getExtractionResult(runId, format);
      const blob = new Blob([format === 'csv' ? data : JSON.stringify(data, null, 2)], {
        type: format === 'csv' ? 'text/csv' : 'application/json'
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `extraction.${format}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      await addMessage(`Download error: ${error.message}`);
    }
  };

  // ================================
  // 🟢 DYNAMIC TABLE: Use ONLY leaf (last) property names as columns
  // Improved: first name, last name, name etc. always shown after page number.
  // ================================
  const renderDynamicTable = (document) => {
    if (!document) return <p className="text-gray-500">No data</p>;

    let pages = Array.isArray(document.pages) ? document.pages : [];
    let docType = document.document_type || document.documentType || '';

    if (pages.length === 0 && document.fields) {
      pages = [{ page: 1, fields: document.fields, tables: document.tables || [] }];
    }
    if (pages.length === 0) return <p className="text-gray-500">No data in pages</p>;

    const keySet = new Set();
    const rowFlatFieldsArr = pages.map(page =>
      page.fields ? flattenFieldsUseOnlyLeafKeys(page.fields, {}, keySet) : {}
    );
    let allKeys = Array.from(keySet).sort();

    // Reorder columns: [first name], [last name], [name], rest
    allKeys = sortNameColumns(allKeys);

    const getColWidthStyle = key => ({
      minWidth: 100,
      maxWidth: 220,
      width: 120,
      whiteSpace: 'pre-line',
      wordBreak: 'break-word'
    });

    return (
      <div className="overflow-x-auto pb-2">
        {/* Table Title (document type) */}
        <h3 className="text-lg font-bold text-gray-700 mb-2 text-center">
          {docType ? docType.toUpperCase() : 'Document'}
        </h3>
        <table className="bg-white border border-gray-200" style={{ minWidth: 'fit-content', tableLayout: 'auto' }}>
          <thead className="bg-gray-50">
            <tr>
              <th
                className="px-3 py-2 border text-left text-sm font-bold text-gray-700 sticky left-0 bg-gray-50 z-10"
                style={{ minWidth: 60, width: 60, maxWidth: 90 }}
              >
                Page #
              </th>
              {allKeys.map(key => (
                <th
                  key={key}
                  className="px-3 py-2 border text-left text-sm font-bold text-gray-700"
                  style={getColWidthStyle(key)}
                >
                  {key}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rowFlatFieldsArr.map((row, idx) => (
              <tr key={idx} className="border-t hover:bg-gray-50">
                <td
                  className="px-3 py-2 border text-sm text-center sticky left-0 bg-white"
                  style={{ minWidth: 60, width: 60, maxWidth: 90 }}
                >
                  {pages[idx]?.page || idx + 1}
                </td>
                {allKeys.map(key => (
                  <td
                    key={key}
                    className="px-3 py-2 border text-sm align-top"
                    style={getColWidthStyle(key)}
                  >
                    {row[key] != null && row[key] !== '' ? String(row[key]) : ''}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  useEffect(() => {
    fetchAllExtractions();
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      {/* Sticky Header */}
      <header className="sticky top-0 z-50 bg-white shadow-lg border-b-4 border-indigo-500">
        <div className="container mx-auto px-6 py-4">
          <h1 className="text-3xl font-bold bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent">
            📋 Form Data Extractor
          </h1>
        </div>
      </header>

      <div className="container mx-auto px-6 py-8">
        {/* Original Upload Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-semibold text-gray-800 mb-6">Upload & Process</h2>
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">Upload Image</label>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleImageUpload}
                className="w-full p-3 border-2 border-dashed border-gray-300 rounded-lg focus:border-indigo-500 focus:outline-none"
              />
            </div>
            {previewUrl && (
              <div className="mb-6">
                <h3 className="text-lg font-medium text-gray-700 mb-2">Preview</h3>
                <img src={previewUrl} alt="Preview" className="max-w-full h-48 object-contain border rounded-lg" />
              </div>
            )}
            <div className="flex space-x-4">
              <button
                onClick={handleExtract}
                disabled={!image || isProcessing}
                className="flex-1 bg-indigo-600 text-white py-3 px-6 rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {isProcessing ? 'Processing...' : 'Extract'}
              </button>
              <button
                onClick={handleCancel}
                className="flex-1 bg-gray-500 text-white py-3 px-6 rounded-lg font-medium hover:bg-gray-600 transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-semibold text-gray-800 mb-6">Processing Status</h2>
            <div
              ref={messagesContainerRef}
              className="mb-6 max-h-64 overflow-y-auto space-y-2"
              style={{ scrollbarWidth: 'thin', scrollbarColor: '#cbd5e1 #f1f5f9' }}
            >
              {messages.map((msg) => (
                <div key={msg.id} className="flex items-start space-x-2 p-3 bg-blue-50 rounded-lg border-l-4 border-blue-500">
                  <div className="w-2 h-2 bg-blue-500 rounded-full mt-2 flex-shrink-0"></div>
                  <div>
                    <p className="text-sm text-gray-700">{msg.text}</p>
                    <p className="text-xs text-gray-500">{msg.timestamp}</p>
                  </div>
                </div>
              ))}
            </div>
            {isProcessing && (
              <div className="mb-6">
                <div className="flex justify-between text-sm text-gray-600 mb-2">
                  <span>Progress</span>
                  <span>{progress}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-3">
                  <div
                    className="bg-gradient-to-r from-blue-500 to-indigo-600 h-3 rounded-full transition-all duration-300 ease-out"
                    style={{ width: `${progress}%` }}
                  ></div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Results Section — ONLY SHOW IF NOT EMPTY */}
        {extractionData && !isEmptyResult(extractionData) && (
          <div className="mt-8 bg-white rounded-xl shadow-lg p-6">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-2xl font-semibold text-gray-800">Extraction Results</h2>
              <div className="flex space-x-2">
                <button
                  onClick={() => setViewMode('json')}
                  className={`px-4 py-2 rounded-lg font-medium ${viewMode === 'json' ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}
                >
                  JSON View
                </button>
                <button
                  onClick={() => setViewMode('table')}
                  className={`px-4 py-2 rounded-lg font-medium ${viewMode === 'table' ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}
                >
                  Table View
                </button>
                <button
                  onClick={() => downloadData('json')}
                  className="px-4 py-2 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700"
                >
                  Download JSON
                </button>
                <button
                  onClick={() => downloadData('csv')}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700"
                >
                  Download CSV
                </button>
              </div>
            </div>
            <div className="border rounded-lg p-4 max-h-96 overflow-x-auto">
              {viewMode === 'json' ? (
                <pre className="text-sm text-gray-800 text-left font-mono whitespace-pre overflow-x-auto">
                  {JSON.stringify(extractionData, null, 2)}
                </pre>
              ) : (
                renderDynamicTable(extractionData)
              )}
            </div>
          </div>
        )}

        {/* === Manage Extractions Section === */}
        <div className="mt-12 bg-white rounded-xl shadow-lg p-6">
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-2xl font-semibold text-gray-800">Manage All Extractions</h2>
            <div className="flex space-x-2">
              <button
                onClick={deleteAllExtractions}
                disabled={loadingExtractions}
                className="px-4 py-2 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700 disabled:opacity-50"
              >
                Delete All
              </button>
              <button
                onClick={fetchAllExtractions}
                disabled={loadingExtractions}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50"
              >
                {loadingExtractions ? 'Loading...' : 'Refresh'}
              </button>
            </div>
          </div>

          {loadingExtractions ? (
            <p className="text-center py-4 text-gray-600">Loading extractions...</p>
          ) : extractions.length === 0 ? (
            <p className="text-center py-4 text-gray-500">No extractions found.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 border text-left">ID</th>
                    <th className="px-4 py-2 border text-left">Run ID</th>
                    <th className="px-4 py-2 border text-left">Doc Type</th>
                    <th className="px-4 py-2 border text-left">Avg Confidence</th>
                    <th className="px-4 py-2 border text-left">Created</th>
                    <th className="px-4 py-2 border text-left">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {extractions.map((ex) => (
                    <tr key={ex.id} className="border-t hover:bg-gray-50">
                      <td className="px-4 py-2 border">{ex.id}</td>
                      <td className="px-4 py-2 border font-mono text-sm">{ex.runId}</td>
                      <td className="px-4 py-2 border">{ex.documentType || '—'}</td>
                      <td className="px-4 py-2 border">{ex.avgConfidence?.toFixed(2) || '—'}</td>
                      <td className="px-4 py-2 border">{new Date(ex.createdAt).toLocaleString()}</td>
                      <td className="px-4 py-2 border space-x-2">
                        <button
                          onClick={() => openViewModal(ex)}
                          className="text-green-600 hover:text-green-800 font-medium"
                        >
                          View
                        </button>
                        <button
                          onClick={() => openEditModal(ex)}
                          className="text-indigo-600 hover:text-indigo-800 font-medium"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => deleteExtraction(ex.id)}
                          className="text-red-600 hover:text-red-800 font-medium"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* === VIEW MODAL === */}
        {viewModalOpen && currentViewData && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
              <div className="p-4 border-b flex justify-between items-center bg-gray-50">
                <h3 className="text-xl font-bold text-gray-800">Extraction Details</h3>
                <button
                  onClick={() => setViewModalOpen(false)}
                  className="text-gray-500 hover:text-gray-700 text-2xl"
                >
                  &times;
                </button>
              </div>
              <div className="p-4 flex space-x-2 border-b">
                <button
                  onClick={() => setViewModeModal('json')}
                  className={`px-4 py-2 rounded-lg font-medium ${viewModeModal === 'json' ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}
                >
                  JSON View
                </button>
                <button
                  onClick={() => setViewModeModal('table')}
                  className={`px-4 py-2 rounded-lg font-medium ${viewModeModal === 'table' ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}
                >
                  Table View
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-4 max-h-[60vh]">
                {viewModeModal === 'json' ? (
                  <pre className="text-sm text-gray-800 text-left font-mono whitespace-pre overflow-x-auto">
                    {JSON.stringify(currentViewData, null, 2)}
                  </pre>
                ) : (
                  renderDynamicTable(currentViewData)
                )}
              </div>
            </div>
          </div>
        )}

        {/* === EDIT MODAL === */}
        {editModalOpen && currentEdit && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
              <div className="p-6">
                <h3 className="text-xl font-bold text-gray-800 mb-4">Edit Extraction #{currentEdit.id}</h3>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Document Type</label>
                    <input
                      type="text"
                      value={tempEditData.documentType || ''}
                      onChange={(e) => setTempEditData({ ...tempEditData, documentType: e.target.value })}
                      className="w-full p-2 border rounded"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Run ID</label>
                    <input
                      type="text"
                      value={tempEditData.runId || ''}
                      onChange={(e) => setTempEditData({ ...tempEditData, runId: e.target.value })}
                      className="w-full p-2 border rounded font-mono"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Avg Confidence</label>
                    <input
                      type="number"
                      step="0.01"
                      value={tempEditData.avgConfidence || ''}
                      onChange={(e) => setTempEditData({ ...tempEditData, avgConfidence: parseFloat(e.target.value) || null })}
                      className="w-full p-2 border rounded"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Result JSON</label>
                    <textarea
                      rows="12"
                      value={tempEditData.resultJson || ''}
                      onChange={(e) => setTempEditData({ ...tempEditData, resultJson: e.target.value })}
                      className="w-full p-2 border rounded font-mono text-sm"
                      style={{
                        fontFamily: 'monospace',
                        textAlign: 'left',
                        direction: 'ltr',
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'break-word',
                        lineHeight: '1.4',
                        fontSize: '13px'
                      }}
                    />
                  </div>
                </div>
                <div className="mt-6 flex justify-end space-x-3">
                  <button
                    onClick={() => setEditModalOpen(false)}
                    className="px-4 py-2 bg-gray-300 text-gray-800 rounded-lg font-medium hover:bg-gray-400"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={updateExtraction}
                    className="px-4 py-2 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700"
                  >
                    Save Changes
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* History Section */}
        {history.length > 0 && (
          <div className="mt-8 bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-semibold text-gray-800 mb-6">Extraction History</h2>
            <div className="overflow-x-auto">
              <table className="min-w-full bg-white border border-gray-200">
                <thead>
                  <tr className="bg-gray-50">
                    <th className="px-4 py-2 border text-left font-medium text-gray-700">Run ID</th>
                    <th className="px-4 py-2 border text-left font-medium text-gray-700">Timestamp</th>
                    <th className="px-4 py-2 border text-left font-medium text-gray-700">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((item) => (
                    <tr key={item.id} className="border-t">
                      <td className="px-4 py-2 border font-mono">{item.id}</td>
                      <td className="px-4 py-2 border">{item.timestamp}</td>
                      <td className="px-4 py-2 border">
                        <span className={`px-2 py-1 rounded-full text-xs ${item.status === 'completed'
                            ? 'bg-green-100 text-green-800'
                            : 'bg-red-100 text-red-800'
                          }`}>
                          {item.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Home;