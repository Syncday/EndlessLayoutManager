package com.syncday.library;

import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A LayoutManager for RecyclerView. Provide endless scroll for Recyclerview.
 * @author Syncday
 */
public class EndlessLayoutManager extends RecyclerView.LayoutManager
        implements RecyclerView.SmoothScroller.ScrollVectorProvider{

    @RecyclerView.Orientation
    private int orientation = RecyclerView.HORIZONTAL;
    protected int pendingScrollTo = RecyclerView.NO_POSITION;
    protected RecyclerView.SmoothScroller smoothScroller;
    protected PointF pointF;

    public EndlessLayoutManager() {

    }

    public EndlessLayoutManager(@RecyclerView.Orientation int orientation) {
        this.orientation = orientation;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if(getItemCount()==0){
            removeAndRecycleAllViews(recycler);
            return;
        }
        if(pendingScrollTo!=RecyclerView.NO_POSITION){
            detachAndFillChild(recycler,pendingScrollTo,0);
            pendingScrollTo = RecyclerView.NO_POSITION;
        }else {
            View oldView = getChildAt(0);
            int position = Math.max(0,oldView==null?RecyclerView.NO_POSITION:getPosition(oldView));
            float offsetPercent = 0;
            if(position<getItemCount()){
                if(oldView!=null){
                    //We try to keep the layout of the childView the same as last time
                    int total = isHorizontal()?getDecoratedMeasuredWidth(oldView):
                            getDecoratedMeasuredHeight(oldView);
                    int offset = isHorizontal()?getDecoratedLeft(oldView):getDecoratedTop(oldView);
                    if(total!=0)
                        offsetPercent = (float) offset/total;
                }
            }else
                position = getItemCount()-1;

            detachAndFillChild(recycler,position,offsetPercent);
        }
    }

    @Override
    public boolean canScrollHorizontally() {
        return isHorizontal();
    }

    @Override
    public boolean canScrollVertically() {
        return !isHorizontal();
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if(!isHorizontal()||dx==0||getChildCount()==0)
            return 0;
        scrollChild(recycler,dx);
        recycleChild(recycler,dx<0);
        return dx;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if(isHorizontal()||dy==0||getChildCount()==0)
            return 0;
        scrollChild(recycler,dy);
        recycleChild(recycler,dy<0);
        return dy;
    }

    @Override
    public void scrollToPosition(int position) {
        if (getItemCount()==0)
            return;
        pendingScrollTo = getEndlessPosition(position);
        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        if(getItemCount()==0)
            return;
        if(smoothScroller==null)
            smoothScroller = new LinearSmoothScroller(recyclerView.getContext());
        smoothScroller.setTargetPosition(getEndlessPosition(position));
        startSmoothScroll(smoothScroller);
    }

    public void setOrientation(@RecyclerView.Orientation int orientation) {
        this.orientation = orientation;
        requestLayout();
    }

    public int getOrientation() {
        return orientation;
    }

    private boolean isHorizontal(){
        return orientation==RecyclerView.HORIZONTAL;
    }

    /**
     * Provide the direction for SmoothScroller. If endView's distance to target is less than
     * startView's distance to target, we will scroll from endView to target, and vice versa.
     * @param targetPosition Scroll to where
     * @return PointF which contain direction value. 1 if scroll from end, else -1 if scroll from start.
     */
    @Nullable
    @Override
    public PointF computeScrollVectorForPosition(int targetPosition) {
        int endPosition = findEndViewPosition();
        int startPosition = findStartViewPosition();
        if(endPosition==RecyclerView.NO_POSITION || startPosition==RecyclerView.NO_POSITION)
            return null;
        int distanceToEnd = targetPosition>=endPosition?
                targetPosition-endPosition:getItemCount()+targetPosition-endPosition;
        int distanceToStart = targetPosition<=startPosition?
                startPosition-targetPosition:getItemCount()+startPosition-targetPosition;
        if(pointF==null)
            pointF = new PointF();
        pointF.x = isHorizontal()?(distanceToEnd>distanceToStart?-1:1):0;
        pointF.y = isHorizontal()?0:(distanceToEnd>distanceToStart?-1:1);
        return pointF;
    }

    /**
     * Scroll all child view with scroll distance. And fill the spacing with new views if in need.
     * @param recycler The recycler of RecyclerView
     * @param scroll The distance of scroll
     */
    private void scrollChild(RecyclerView.Recycler recycler, int scroll){
        if(isHorizontal())
            offsetChildrenHorizontal(-scroll);
        else
            offsetChildrenVertical(-scroll);
        if(scroll>0){
            View endView = getChildAt(getChildCount()-1);
            if(endView==null)
                return;
            if(isHorizontal()?endView.getRight()<getWidth():endView.getBottom()<getHeight())
                addAndFillChild(recycler, true);
        }else {
            View startView = getChildAt(0);
            if(startView==null)
                return;
            if(isHorizontal()?startView.getLeft()>0:startView.getTop()>0)
                addAndFillChild(recycler,false);
        }
    }

    /**
     * Detach all views and then re-layout views from position.
     * @param recycler The recycler of RecyclerView
     * @param startPosition The position to start layout
     * @param offset The view's start side offset to RecyclerView's start side.
     */
    private void detachAndFillChild(RecyclerView.Recycler recycler, int startPosition,float offset){
        detachAndScrapAttachedViews(recycler);
        View view = recycler.getViewForPosition(startPosition);
        addView(view);
        measureChildWithMargins(view,0,0);
        int width = getDecoratedMeasuredWidth(view);
        int height = getDecoratedMeasuredHeight(view);
        int offsetDistance = isHorizontal()?(int)(width*offset):(int)(height*offset);
        int l = isHorizontal()?offsetDistance:0;
        int r = l+width;
        int t = isHorizontal()?0:offsetDistance;
        int b = t+height;
        layoutDecoratedWithMargins(view,l,t,r,b);
        addAndFillChild(recycler,true);
    }

    /**
     * Add new views to the end or start side. If RecyclerView has no child view, it will do nothing.
     * @param recycler The recycler of RecyclerView
     * @param attachToEnd Is add to the end side
     */
    private void addAndFillChild(RecyclerView.Recycler recycler, boolean attachToEnd){
        boolean hasSpacing = true;
        int position;
        View anchor;
        View newChild;
        int width,height;
        int l,t,r,b;
        while (hasSpacing){
            anchor = getChildAt(attachToEnd?getChildCount()-1:0);
            position = getEndlessPosition(attachToEnd?
                    findEndViewPosition()+1:
                    findStartViewPosition()-1);
            if(position == RecyclerView.NO_POSITION || anchor==null)
                return;
            newChild = recycler.getViewForPosition(position);
            addView(newChild,attachToEnd?-1:0);
            measureChildWithMargins(newChild,0,0);
            width = getDecoratedMeasuredWidth(newChild);
            height = getDecoratedMeasuredHeight(newChild);

            if(attachToEnd){
                l = isHorizontal()?anchor.getRight():0;
                t = isHorizontal()?0:anchor.getBottom();
                r = l+width;
                b = t+height;
                layoutDecoratedWithMargins(newChild,l,t,r,b);
                hasSpacing = isHorizontal()?
                        newChild.getRight()<getWidth():
                        newChild.getBottom()<getHeight();
            }else {
                l = isHorizontal()?anchor.getLeft()-width:0;
                t = isHorizontal()?0:anchor.getTop()-height;
                r = l+width;
                b = t+height;
                layoutDecoratedWithMargins(newChild,l,t,r,b);
                hasSpacing = isHorizontal()?
                        newChild.getLeft()>0:
                        newChild.getTop()>0;
            }
        }
    }


    /**
     * Remove and Recycle views which is unseeable
     * @param recycler Recycler of RecyclerView
     * @param recycleEnd Is recycle start from the end side.
     * @return Count of view that was recycled.
     */
    private int recycleChild(RecyclerView.Recycler recycler,boolean recycleEnd){
        int count = 0;
        View view;
        while ((view = recycleEnd?getChildAt(getChildCount()-1):getChildAt(0))!=null && (isHorizontal()?
                recycleEnd?view.getLeft()>=getWidth():view.getRight()<=0:
                recycleEnd?view.getTop()>=getHeight():view.getBottom()<=0)){
            removeAndRecycleView(view,recycler);
            count++;
        }
        return count;
    }

    /**
     * Find the end view's position which had attach to RecyclerView
     * @return The position index
     */
    public int findStartViewPosition(){
        View startView = getChildAt(0);
        return startView==null?RecyclerView.NO_POSITION:getPosition(startView);
    }

    /**
     * Find the start view's position which had attach to RecyclerView
     * @return The position index
     */
    public int findEndViewPosition(){
        View endView = getChildAt(getChildCount()-1);
        return endView==null?RecyclerView.NO_POSITION:getPosition(endView);
    }

    /**
     * Get the real position.
     * For example, when the itemCount is 8:
     * if pass -1 then will return 7 , if pass 8 then will return 0
     * @param position Any position you want
     * @return Real position. [0, itemCount]
     */
    private int getEndlessPosition(int position){
        final int itemCount = getItemCount();
        if(itemCount<=0)
            return RecyclerView.NO_POSITION;
        return position<0?
                itemCount-((-position)%itemCount):position>=itemCount?
                position%itemCount :
                position;
    }

}
