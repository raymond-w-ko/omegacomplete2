" script load guard
if exists('g:omegacomplete2_loaded')
    finish
endif
let g:omegacomplete2_loaded = 1
if !has('java')
    finish
end

if !exists("g:omegacomplete2_normal_hi_cmds")
    let g:omegacomplete2_normal_hi_cmds=[
    \ "hi Pmenu guifg=#00ff00 guibg=#003300 gui=none " .
             \ "ctermbg=022 ctermfg=046 cterm=none",
    \ "hi PmenuSel guifg=#003300 guibg=#00ff00 gui=none " .
                \ "ctermbg=046 ctermfg=022 cterm=none",
        \ ]
endif
if !exists("g:omegacomplete2_corrections_hi_cmd")
    let g:omegacomplete2_corrections_hi_cmd=[
    \ "hi Pmenu guifg=#ffff00 guibg=#333300 gui=none " .
              \"ctermbg=058 ctermfg=011 cterm=none",
    \ "hi PmenuSel guifg=#333300 guibg=#ffff00 gui=none " .
                \ "ctermbg=011 ctermfg=058 cterm=none",
        \ ]
endif

let s:omegacomplete2_path = ''
let s:current_hilight = 'undefined'

function s:Init()
    " find where clojure code exists
    let path_list = split(&runtimepath, ',')
    for path in path_list
        let candidate_path = path . '/clojure/com/raymondwko/omegacomplete2'
        if isdirectory(candidate_path)
            let s:omegacomplete2_path = candidate_path
            break
        endif
    endfor
    if len(s:omegacomplete2_path) == 0
        echoerr 'could not find omegacomplete2 Clojure directory'
        return
    end

    set completefunc=Omegacomplete2Func

    set completeopt+=menu
    set completeopt+=menuone

    call s:LoadMappings()

    " if we are running in Win32, we have to convert backslashes as the
    " Clojure reader doesn't like them
    let s:omegacomplete2_path = substitute(s:omegacomplete2_path, '\\', '/', 'g')

    call <SID>ReloadClojure()

    javarepl clojure
    java (com.raymondwko.omegacomplete2/init)
endfunction

function <SID>ReloadClojure()
    javarepl clojure
    exe 'java (load-string (slurp "' . s:omegacomplete2_path . '/core.clj"))'
endfunction

function s:LoadMappings()
    command Omegacomplete2Reload :call <SID>ReloadClojure()

    nnoremap <silent> i i<C-r>=omegacomplete2#OnInsertModeEvent()<CR>
    nnoremap <silent> I I<C-r>=omegacomplete2#OnInsertModeEvent()<CR>
    nnoremap <silent> a a<C-r>=omegacomplete2#OnInsertModeEvent()<CR>
    nnoremap <silent> A A<C-r>=omegacomplete2#OnInsertModeEvent()<CR>
    nnoremap <silent> R R<C-r>=omegacomplete2#OnInsertModeEvent()<CR>

    let s:keys_mapping_driven =
        \ [
        \ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        \ 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        \ 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        \ 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        \ '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        \ '-', '_', '<Space>', '<C-h>', '<BS>', 
        \ ]

        "'~', '^', '.', ',', ':', '!', '#', '=', '%', '$', '@', '<', '>', '/',
        "'\'
 
    for key in s:keys_mapping_driven
        exe printf('inoremap <silent> %s %s<C-r>=omegacomplete2#OnInsertModeEvent()<CR>',
                 \ key, key)
    endfor
endfunction

function omegacomplete2#OnInsertModeEvent()
    " disable when paste mode is active
    if &paste
        return ''
    endif

    javarepl clojure

    call <SID>SyncCurrentBuffer()

    let g:omegacomplete2_results=[]

    exe 'java (com.raymondwko.omegacomplete2/calculate-and-fill-results)'

    if len(g:omegacomplete2_results) == 0
        " try to show popup menu, but fail and reset completion status
        return "\<C-x>\<C-u>"
    else
        exe 'java (com.raymondwko.omegacomplete2/set-is-corrections-only)'
        if is_corrections_only && s:current_hilight != 'corrections'
            for cmd in g:omegacomplete2_corrections_hi_cmd
                exe cmd
            endfor
            let s:current_hilight = 'corrections'
        elseif !is_corrections_only && s:current_hilight != 'normal'
            for cmd in g:omegacomplete2_normal_hi_cmds
                exe cmd
            endfor
            let s:current_hilight = 'normal'
        endif
        " show actual popup
        return "\<C-x>\<C-u>\<C-p>"
    endif
endfunction

function <SID>SyncCurrentBuffer()
    javarepl clojure
    exe 'java (com.raymondwko.omegacomplete2/capture-buffer ' . bufnr('%') . ')'
endfunction

augroup omegacomplete2
    autocmd!
    autocmd BufLeave * call <SID>SyncCurrentBuffer()
    autocmd InsertEnter * call <SID>SyncCurrentBuffer()
    autocmd BufReadPost * call <SID>SyncCurrentBuffer()
augroup END

function Omegacomplete2Func(findstart, base)
    if a:findstart
        if len(g:omegacomplete2_results) == 0
            if (v:version > 702)
                return -3
            else
                return -1
            endif
        endif

        let index = col('.') - 2
        let line = getline('.')
        while 1
            if index == -1
                break
            endif
            if match(line[index], '[a-zA-Z0-9_\-]') == -1
                break
            endif

            let index = index - 1
        endwhile
        let result = index + 1
        return result
    else
        if (v:version > 702)
            return {'words' : g:omegacomplete2_results}
        else
            return g:omegacomplete2_results
        endif
    endif
endfunction

call s:Init()
