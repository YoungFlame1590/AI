const PX_PER_MM = 3.2;
const SIZE_PRESETS = {
  "90x54mm": { widthMm: 90, heightMm: 54, bleedMm: 3 },
  "90×54mm": { widthMm: 90, heightMm: 54, bleedMm: 3 },
  A5: { widthMm: 148, heightMm: 210, bleedMm: 3 },
  A4: { widthMm: 210, heightMm: 297, bleedMm: 3 },
  A3: { widthMm: 297, heightMm: 420, bleedMm: 5 },
  A2: { widthMm: 420, heightMm: 594, bleedMm: 5 },
  "800x2000mm": { widthMm: 800, heightMm: 2000, bleedMm: 10 },
  "800×2000mm": { widthMm: 800, heightMm: 2000, bleedMm: 10 },
  "210x99mm": { widthMm: 210, heightMm: 99, bleedMm: 3 },
  "210×99mm": { widthMm: 210, heightMm: 99, bleedMm: 3 },
  "210x285mm": { widthMm: 210, heightMm: 285, bleedMm: 3 },
  "210×285mm": { widthMm: 210, heightMm: 285, bleedMm: 3 },
};

export function mountDesignEditor(containerEl, project, template, versions = [], handlers = {}) {
  if (!window.fabric || !window.QRCode) {
    containerEl.innerHTML = "<p class=\"empty wide\">设计编辑器资源未加载，请刷新页面。</p>";
    return;
  }
  const size = sizeFor(template?.sizeName);
  containerEl.innerHTML = editorMarkup(project, template, size, versions);

  const canvas = new fabric.Canvas(containerEl.querySelector(".design-main-canvas"), {
    preserveObjectStacking: true,
    backgroundColor: "#ffffff",
  });
  const shell = containerEl.querySelector(".design-canvas-shell");
  const bleedGuide = containerEl.querySelector(".design-bleed-guide");
  const pxW = Math.round(size.widthMm * PX_PER_MM);
  const pxH = Math.round(size.heightMm * PX_PER_MM);
  canvas.setWidth(pxW);
  canvas.setHeight(pxH);
  shell.style.width = `${pxW}px`;
  shell.style.height = `${pxH}px`;
  const scale = Math.min(1, 620 / Math.max(pxW, pxH));
  shell.style.transform = `scale(${scale})`;
  shell.style.transformOrigin = "center center";
  const bleedPx = size.bleedMm * PX_PER_MM;
  bleedGuide.style.left = `${bleedPx}px`;
  bleedGuide.style.top = `${bleedPx}px`;
  bleedGuide.style.width = `${pxW - bleedPx * 2}px`;
  bleedGuide.style.height = `${pxH - bleedPx * 2}px`;

  loadCanvas(canvas, project?.canvasJson, template?.canvasJson);
  wireEditor(containerEl, canvas, project, template, versions, handlers);
}

function editorMarkup(project, template, size, versions) {
  return `
    <section class="wysiwyg-editor wide">
      <header class="design-editor-head">
        <div>
          <strong>${escapeHtml(project?.title || "在线设计项目")}</strong>
          <span>${escapeHtml(project?.projectNo || "-")} · ${escapeHtml(template?.title || "模板")} · ${escapeHtml(template?.sizeName || "A4")}</span>
        </div>
        <div class="design-spec-lock">${size.widthMm} x ${size.heightMm} mm · 出血 ${size.bleedMm}mm · 尺寸由模板锁定</div>
      </header>
      <div class="design-editor-grid">
        <aside class="design-tools">
          <h3>添加元素</h3>
          <button type="button" data-design-action="addText"><span>T</span>添加文字</button>
          <button type="button" data-design-action="uploadImage"><span>▨</span>上传/替换图片</button>
          <button type="button" data-design-action="addLogo"><span>◆</span>添加Logo</button>
          <button type="button" data-design-action="addQr"><span>▦</span>生成二维码</button>
          <input class="design-image-input" type="file" accept="image/*">
          <input class="design-logo-input" type="file" accept="image/*">
          <h3>图层</h3>
          <div class="design-tool-row">
            <button type="button" data-design-action="bringForward">上移</button>
            <button type="button" data-design-action="sendBackward">下移</button>
          </div>
          <button type="button" class="danger" data-design-action="deleteObject">删除所选</button>
        </aside>
        <main class="design-canvas-area">
          <div class="design-canvas-shell">
            <canvas class="design-main-canvas"></canvas>
            <div class="design-bleed-guide"></div>
          </div>
        </main>
        <aside class="design-props">
          <h3>属性</h3>
          <p class="design-prop-empty">选择画布元素后可编辑字号、颜色、加粗、对齐和图片透明度。</p>
          <div class="design-text-props hidden">
            <label>字号<input class="design-font-size" type="range" min="10" max="120" value="28"></label>
            <label>颜色<input class="design-font-color" type="color" value="#20232B"></label>
            <div class="design-tool-row">
              <button type="button" data-design-action="toggleBold"><b>B</b> 加粗</button>
              <button type="button" data-design-action="toggleAlign">对齐</button>
            </div>
          </div>
          <div class="design-image-props hidden">
            <label>不透明度<input class="design-opacity" type="range" min="0" max="100" value="100"></label>
            <button type="button" data-design-action="replaceImage">替换图片</button>
          </div>
          <section class="design-order-panel">
            <h3>提交订单</h3>
            <label>尺寸<input value="${escapeAttribute(template?.sizeName || "A4")}" disabled></label>
            <label>纸张<select class="design-paper">
              <option>标准纸</option><option>A4 80g</option><option>300g铜版纸</option><option>写真材料</option><option>高阶艺术纸</option>
            </select></label>
            <label>工艺<select class="design-craft">
              <option>无特殊工艺</option><option>覆膜</option><option>烫金</option><option>过UV</option><option>裁切压线</option>
            </select></label>
            <label>份数<input class="design-copies" type="number" min="1" max="10000" value="${Number(template?.defaultCopies || 100)}"></label>
          </section>
          <section class="design-versions">
            <h3>历史版本</h3>
            <div class="design-version-list">${versionListMarkup(versions, project?.currentVersionNo)}</div>
          </section>
        </aside>
      </div>
      <footer class="design-editor-actions">
        <button type="button" data-design-action="saveVersion" class="primary">保存版本</button>
        <button type="button" data-design-action="submitOrder" class="primary">提交订单</button>
      </footer>
    </section>
    <div class="design-qr-mask hidden">
      <div class="design-qr-modal">
        <h3>生成二维码</h3>
        <input class="design-qr-text" placeholder="输入链接或文字内容">
        <div class="design-tool-row">
          <button type="button" data-design-action="cancelQr">取消</button>
          <button type="button" data-design-action="confirmQr" class="primary">生成并插入</button>
        </div>
      </div>
    </div>
    <div class="design-qr-hidden"></div>
  `;
}

function wireEditor(container, canvas, project, template, versions, handlers) {
  const imageInput = container.querySelector(".design-image-input");
  const logoInput = container.querySelector(".design-logo-input");
  let replacingImage = null;

  container.addEventListener("click", async (event) => {
    const versionItem = event.target.closest("[data-version-no]");
    if (versionItem) {
      const versionNo = Number(versionItem.dataset.versionNo);
      const detail = await handlers.onRestoreVersion?.(versionNo);
      reloadFromDetail(container, canvas, detail);
      return;
    }

    const button = event.target.closest("[data-design-action]");
    if (!button) return;
    const action = button.dataset.designAction;
    if (action === "addText") addText(canvas);
    if (action === "uploadImage") imageInput.click();
    if (action === "addLogo") logoInput.click();
    if (action === "addQr") container.querySelector(".design-qr-mask").classList.remove("hidden");
    if (action === "cancelQr") container.querySelector(".design-qr-mask").classList.add("hidden");
    if (action === "confirmQr") addQr(container, canvas);
    if (action === "bringForward") layer(canvas, "forward");
    if (action === "sendBackward") layer(canvas, "backward");
    if (action === "deleteObject") deleteSelected(canvas);
    if (action === "toggleBold") toggleBold(canvas);
    if (action === "toggleAlign") toggleAlign(canvas);
    if (action === "replaceImage") {
      replacingImage = canvas.getActiveObject()?.type === "image" ? canvas.getActiveObject() : null;
      if (replacingImage) imageInput.click();
    }
    if (action === "saveVersion") {
      const detail = await handlers.onSaveVersion?.(JSON.stringify(canvas.toJSON(["kind"])));
      reloadFromDetail(container, canvas, detail);
    }
    if (action === "submitOrder") {
      await handlers.onSubmitOrder?.({
        paperType: container.querySelector(".design-paper").value,
        craftType: container.querySelector(".design-craft").value,
        copies: Number(container.querySelector(".design-copies").value || template?.defaultCopies || 1),
        deliveryMode: "到店自提",
        priority: "普通",
      }, JSON.stringify(canvas.toJSON(["kind"])));
    }
  });

  imageInput.addEventListener("change", () => {
    const file = imageInput.files?.[0];
    if (file) readImage(file, (img) => {
      if (replacingImage) {
        img.scale((replacingImage.width * replacingImage.scaleX) / img.width);
        img.set({ left: replacingImage.left, top: replacingImage.top, angle: replacingImage.angle });
        canvas.remove(replacingImage);
        replacingImage = null;
      } else {
        scaleImageToWidth(img, canvas.getWidth() * 0.58);
        img.set({ left: 40, top: 40 });
      }
      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
    });
    imageInput.value = "";
  });

  logoInput.addEventListener("change", () => {
    const file = logoInput.files?.[0];
    if (file) readImage(file, (img) => {
      const targetW = canvas.getWidth() * 0.18;
      scaleImageToWidth(img, targetW);
      img.set({ left: canvas.getWidth() - targetW - 16, top: canvas.getHeight() - img.height * img.scaleY - 16, kind: "logo" });
      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
    });
    logoInput.value = "";
  });

  canvas.on("selection:created", () => refreshProps(container, canvas));
  canvas.on("selection:updated", () => refreshProps(container, canvas));
  canvas.on("selection:cleared", () => refreshProps(container, canvas));
  container.querySelector(".design-font-size").addEventListener("input", (event) => {
    const obj = canvas.getActiveObject();
    if (obj) {
      obj.set("fontSize", Number(event.target.value));
      canvas.renderAll();
    }
  });
  container.querySelector(".design-font-color").addEventListener("input", (event) => {
    const obj = canvas.getActiveObject();
    if (obj) {
      obj.set("fill", event.target.value);
      canvas.renderAll();
    }
  });
  container.querySelector(".design-opacity").addEventListener("input", (event) => {
    const obj = canvas.getActiveObject();
    if (obj) {
      obj.set("opacity", Number(event.target.value) / 100);
      canvas.renderAll();
    }
  });
  refreshProps(container, canvas);
}

function reloadFromDetail(container, canvas, detail) {
  if (!detail?.project) return;
  loadCanvas(canvas, detail.project.canvasJson, detail.template?.canvasJson);
  container.querySelector(".design-version-list").innerHTML = versionListMarkup(
    detail.versions || [],
    detail.project.currentVersionNo,
  );
}

function loadCanvas(canvas, projectJson, templateJson) {
  const raw = projectJson || templateJson || "{}";
  try {
    const parsed = JSON.parse(raw);
    canvas.clear();
    canvas.backgroundColor = "#ffffff";
    if (parsed.version || parsed.objects?.some((item) => item.type === "i-text" || item.type === "image")) {
      canvas.loadFromJSON(parsed, () => canvas.renderAll());
      return;
    }
    loadLegacyObjects(canvas, parsed.objects || []);
  } catch {
    loadLegacyObjects(canvas, [{ type: "text", text: "设计稿内容待修正", x: 80, y: 80 }]);
  }
}

function loadLegacyObjects(canvas, objects) {
  canvas.clear();
  canvas.backgroundColor = "#ffffff";
  for (const item of objects) {
    if (item.type === "text") {
      canvas.add(new fabric.IText(item.text || "文本", { left: Number(item.x || 60), top: Number(item.y || 60), fontSize: 24, fill: "#20232B", fontFamily: "Microsoft YaHei, Arial" }));
    } else if (item.type === "qr") {
      canvas.add(new fabric.Rect({ left: Number(item.x || 60), top: Number(item.y || 60), width: 72, height: 72, fill: "#eef6ff", stroke: "#236f66", kind: "qr" }));
    } else {
      canvas.add(new fabric.Rect({ left: Number(item.x || 60), top: Number(item.y || 60), width: 140, height: 86, fill: "#eef6ff", stroke: "#2f6cbd", kind: item.type }));
      canvas.add(new fabric.Text(item.text || item.type || "图片", { left: Number(item.x || 60) + 10, top: Number(item.y || 60) + 32, fontSize: 14, fill: "#2f6cbd" }));
    }
  }
  canvas.renderAll();
}

function addText(canvas) {
  const text = new fabric.IText("双击编辑文字", {
    left: canvas.getWidth() / 2 - 72,
    top: canvas.getHeight() / 2 - 18,
    fontSize: 28,
    fill: "#20232B",
    fontFamily: "Microsoft YaHei, Arial",
  });
  canvas.add(text);
  canvas.setActiveObject(text);
  canvas.renderAll();
}

function readImage(file, callback) {
  const reader = new FileReader();
  reader.onload = (event) => fabric.Image.fromURL(event.target.result, callback);
  reader.readAsDataURL(file);
}

function scaleImageToWidth(img, maxW) {
  if (img.width > maxW) {
    img.scale(maxW / img.width);
  }
}

function addQr(container, canvas) {
  const text = container.querySelector(".design-qr-text").value.trim();
  if (!text) return;
  const hidden = container.querySelector(".design-qr-hidden");
  hidden.innerHTML = "";
  new QRCode(hidden, { text, width: 180, height: 180, correctLevel: QRCode.CorrectLevel.M });
  setTimeout(() => {
    const node = hidden.querySelector("canvas") || hidden.querySelector("img");
    const dataUrl = node.tagName.toLowerCase() === "canvas" ? node.toDataURL() : node.src;
    fabric.Image.fromURL(dataUrl, (img) => {
      const targetW = canvas.getWidth() * 0.20;
      scaleImageToWidth(img, targetW);
      img.set({ left: 20, top: canvas.getHeight() - targetW - 20, kind: "qr" });
      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
      container.querySelector(".design-qr-mask").classList.add("hidden");
      container.querySelector(".design-qr-text").value = "";
    });
  }, 60);
}

function layer(canvas, direction) {
  const obj = canvas.getActiveObject();
  if (!obj) return;
  if (direction === "forward") canvas.bringForward(obj);
  if (direction === "backward") canvas.sendBackwards(obj);
  canvas.renderAll();
}

function deleteSelected(canvas) {
  const obj = canvas.getActiveObject();
  if (obj) {
    canvas.remove(obj);
    canvas.renderAll();
  }
}

function toggleBold(canvas) {
  const obj = canvas.getActiveObject();
  if (obj && ["i-text", "text"].includes(obj.type)) {
    obj.set("fontWeight", obj.fontWeight === "bold" ? "normal" : "bold");
    canvas.renderAll();
  }
}

function toggleAlign(canvas) {
  const obj = canvas.getActiveObject();
  if (!obj || !["i-text", "text"].includes(obj.type)) return;
  const options = ["left", "center", "right"];
  obj.set("textAlign", options[(options.indexOf(obj.textAlign) + 1) % options.length]);
  canvas.renderAll();
}

function refreshProps(container, canvas) {
  const obj = canvas.getActiveObject();
  container.querySelector(".design-prop-empty").classList.toggle("hidden", Boolean(obj));
  container.querySelector(".design-text-props").classList.toggle("hidden", !(obj && ["i-text", "text"].includes(obj.type)));
  container.querySelector(".design-image-props").classList.toggle("hidden", !(obj && obj.type === "image"));
  if (obj && ["i-text", "text"].includes(obj.type)) {
    container.querySelector(".design-font-size").value = obj.fontSize || 28;
    container.querySelector(".design-font-color").value = normalizeColor(obj.fill);
  }
  if (obj?.type === "image") {
    container.querySelector(".design-opacity").value = Math.round((obj.opacity ?? 1) * 100);
  }
}

function sizeFor(sizeName) {
  return SIZE_PRESETS[sizeName] || parseSize(sizeName) || SIZE_PRESETS.A4;
}

function parseSize(sizeName = "") {
  const match = String(sizeName).match(/(\d+(?:\.\d+)?)\s*[x×]\s*(\d+(?:\.\d+)?)\s*mm/i);
  if (!match) return null;
  return { widthMm: Number(match[1]), heightMm: Number(match[2]), bleedMm: 3 };
}

function versionListMarkup(versions, currentVersionNo) {
  if (!versions.length) {
    return "<p class=\"empty\">还没有保存的版本</p>";
  }
  return versions.map((version) => {
    const current = Number(version.versionNo) === Number(currentVersionNo);
    return `
    <button
      type="button"
      class="design-version-item${current ? " current" : ""}"
      data-version-no="${escapeAttribute(version.versionNo)}"
      aria-pressed="${current}"
    >
      <span>V${escapeHtml(version.versionNo)}</span>
      <strong>${escapeHtml(version.label || "历史版本")}${current ? "（当前）" : ""}</strong>
      <small>${escapeHtml(String(version.createdAt || "").replace("T", " ").slice(0, 16))}</small>
    </button>
  `;
  }).join("");
}

function normalizeColor(value) {
  return typeof value === "string" && value.startsWith("#") ? value : "#20232B";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
  return escapeHtml(value);
}
