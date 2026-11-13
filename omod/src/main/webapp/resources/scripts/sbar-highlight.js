/*
 * Highlights abnormal laboratory result lines (flag such as [↓ LOW] or [↑↑ CRITICAL HIGH]) in saved
 * SBAR text: every element with class "sbar-cell" or "ph-sbar-text". Only text nodes are changed, so
 * the surrounding markup is kept.
 */
(function() {
    var LAB_FLAG = /\[(?:↑↑ CRITICAL HIGH|↓↓ CRITICAL LOW|↑ HIGH|↓ LOW|ABNORMAL)\]|\((?:critical high|critical low|high|low|abnormal)\)(?= \(|\s*$)/;

    function highlightTextNode(node) {
        var lines = node.nodeValue.split('\n');
        var found = false;
        for (var i = 0; i < lines.length; i++) if (LAB_FLAG.test(lines[i])) found = true;
        if (!found) return;
        var fragment = document.createDocumentFragment();
        for (var j = 0; j < lines.length; j++) {
            if (j > 0) fragment.appendChild(document.createTextNode('\n'));
            if (LAB_FLAG.test(lines[j])) {
                var mark = document.createElement('mark');
                mark.className = 'ph-lab-flag';
                mark.appendChild(document.createTextNode(lines[j]));
                fragment.appendChild(mark);
            } else if (lines[j]) {
                fragment.appendChild(document.createTextNode(lines[j]));
            }
        }
        node.parentNode.replaceChild(fragment, node);
    }

    function highlight(root) {
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
        var nodes = [];
        while (walker.nextNode()) {
            var parent = walker.currentNode.parentNode;
            if (parent && parent.nodeName !== 'TEXTAREA' && parent.nodeName !== 'MARK') nodes.push(walker.currentNode);
        }
        for (var i = 0; i < nodes.length; i++) highlightTextNode(nodes[i]);
    }

    function run() {
        var cells = document.querySelectorAll('.sbar-cell, .ph-sbar-text');
        for (var i = 0; i < cells.length; i++) highlight(cells[i]);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
})();
